package org.basex.build;

import org.basex.core.MainOptions;
import org.basex.core.StaticOptions;
import org.basex.data.Data;
import org.basex.data.DataClip;
import org.basex.data.DiskData;
import org.basex.data.MetaData;
import org.basex.index.name.Names;
import org.basex.io.IO;
import org.basex.io.in.DataInput;
import org.basex.io.out.DataOutput;
import org.basex.io.out.TableOutput;
import org.basex.io.random.TableAccess;
import org.basex.io.random.TableDiskAccess;
import org.basex.util.Compress;
import org.basex.util.Performance;
import org.basex.util.Prop;
import org.basex.util.Token;
import org.basex.util.Util;

import java.io.Closeable;
import java.io.IOException;

import static org.basex.core.Text.DOTS;
import static org.basex.data.DataText.DATAATV;
import static org.basex.data.DataText.DATATBL;
import static org.basex.data.DataText.DATATMP;
import static org.basex.data.DataText.DATATXT;

/**
 * This class creates a database instance on disk.
 * The storage layout is described in the {@link Data} class.
 *
 * @author BaseX Team 2005-15, BSD License
 * @author Christian Gruen
 */
public final class DiskBuilder extends Builder implements Closeable {
  /** Text compressor. */
  private final Compress comp = new Compress();

  /** Database table. */
  private DataOutput tout;
  /** Database texts. */
  private DataOutput xout;
  /** Database values. */
  private DataOutput vout;
  /** Output stream for temporary values. */
  private DataOutput sout;

  /** Static options. */
  private final StaticOptions sopts;
  /** Closed flag. */
  private boolean closed;
  /** Debug counter. */
  private int c;

  /**
   * Constructor.
   * @param name name of database
   * @param parser parser
   * @param sopts static options
   * @param opts main options
   */
  public DiskBuilder(final String name, final Parser parser, final StaticOptions sopts,
      final MainOptions opts) {
    super(name, parser);
    this.sopts = sopts;
    meta = new MetaData(dbname, opts, sopts);
  }

  @Override
  public DiskData build() throws IOException {
    meta.assign(parser);
    meta.dirty = true;

    // calculate optimized output buffer sizes to reduce disk fragmentation
    final Runtime rt = Runtime.getRuntime();
    final long max = Math.min(1 << 22, rt.maxMemory() - rt.freeMemory() >> 2);
    int bs = (int) Math.min(meta.filesize, max);
    bs = Math.max(IO.BLOCKSIZE, bs - bs % IO.BLOCKSIZE);

    // drop old database (if available) and create new one
//    DropDB.drop(dbname, sopts);
    sopts.dbpath(dbname).md();

    elemNames = new Names(meta);
    attrNames = new Names(meta);
    try {
      tout = new DataOutput(new TableOutput(meta, DATATBL));
      xout = new DataOutput(meta.dbfile(DATATXT), bs);
      vout = new DataOutput(meta.dbfile(DATAATV), bs);
      sout = new DataOutput(meta.dbfile(DATATMP), bs);

      final Performance perf = Prop.debug ? new Performance() : null;
      Util.debug(tit() + DOTS);
      parse();
      if(Prop.debug) Util.errln(" " + perf + " (" + Performance.getMemory() + ')');

    } catch(final IOException ex) {
      try { close(); } catch(final IOException ignored) { }
      throw ex;
    }
    close();

    // copy temporary values into database table
    try(final DataInput in = new DataInput(meta.dbfile(DATATMP))) {
      final TableAccess ta = new TableDiskAccess(meta, true);
      try {
        for(; spos < ssize; ++spos) ta.write4(in.readNum(), 8, in.readNum());
      } finally {
        ta.close();
      }
    }
    meta.dbfile(DATATMP).delete();

    // return database instance
    return new DiskData(meta, elemNames, attrNames, path, nspaces);
  }

//  @Override
  public void abort() {
    try {
      close();
    } catch(final IOException ex) {
      Util.debug(ex);
    }
    //if(meta != null) DropDB.drop(meta.name, sopts);
  }

  @Override
  public DataClip dataClip() throws IOException {
    return new DataClip(build());
  }

  @Override
  public void close() throws IOException {
    if(closed) return;
    closed = true;
    if(tout != null) tout.close();
    if(xout != null) xout.close();
    if(vout != null) vout.close();
    if(sout != null) sout.close();
    parser.close();
    tout = null;
    xout = null;
    vout = null;
    sout = null;
  }

  @Override
  protected void addDoc(final byte[] value) throws IOException {
    tout.write1(Data.DOC);
    tout.write2(0);
    tout.write5(textRef(value, true));
    tout.write4(0);
    tout.write4(meta.size++);
  }

  @Override
  protected void addElem(final int dist, final int nameId, final int asize, final int uriId,
      final boolean ne) throws IOException {

    tout.write1(asize << 3 | Data.ELEM);
    tout.write2((ne ? 1 << 15 : 0) | nameId);
    tout.write1(uriId);
    tout.write4(dist);
    tout.write4(asize);
    tout.write4(meta.size++);

    if(Prop.debug && (c++ & 0x7FFFF) == 0) Util.err(".");
  }

  @Override
  protected void addAttr(final int nameId, final byte[] value, final int dist, final int uriId)
      throws IOException {

    tout.write1(dist << 3 | Data.ATTR);
    tout.write2(nameId);
    tout.write5(textRef(value, false));
    tout.write4(uriId);
    tout.write4(meta.size++);
  }

  @Override
  protected void addText(final byte[] value, final int dist, final byte kind) throws IOException {
    tout.write1(kind);
    tout.write2(0);
    tout.write5(textRef(value, true));
    tout.write4(dist);
    tout.write4(meta.size++);
  }

  @Override
  protected void setSize(final int pre, final int size) throws IOException {
    sout.writeNum(pre);
    sout.writeNum(size);
    ++ssize;
  }

  /**
   * Calculates the text offset and writes the text value.
   * @param value value to be inlined
   * @param text text/attribute flag
   * @return inline value or text position
   * @throws IOException I/O exception
   */
  private long textRef(final byte[] value, final boolean text) throws IOException {
    // inline integer value
    final long v = Token.toSimpleInt(value);
    if(v != Integer.MIN_VALUE) return v | IO.OFFNUM;

    // store text to heap file
    final DataOutput store = text ? xout : vout;
    final long off = store.size();
    final byte[] val = comp.pack(value);
    store.writeToken(val);
    return val == value ? off : off | IO.OFFCOMP;
  }
}
