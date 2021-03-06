package org.basex.query.value.item;

import java.io.*;

import org.basex.core.*;
import org.basex.io.*;
import org.basex.io.in.*;
import org.basex.query.*;
import org.basex.query.func.*;
import org.basex.util.*;

/**
 * Streamable string item ({@code xs:string}).
 *
 * @author BaseX Team 2005-15, BSD License
 * @author Christian Gruen
 */
public final class StrStream extends AStr {
  /** Input reference. */
  private final IO input;
  /** Encoding (optional). */
  private final String encoding;
  /** Error message. */
  private final QueryError error;
  /** Validation flag. */
  private final boolean validate;

  /**
   * Constructor.
   * @param input input
   * @param encoding encoding (may be null)
   * @param qc query context
   * @param error error message to be thrown
   */
  public StrStream(final IO input, final String encoding, final QueryError error,
      final QueryContext qc) {

    this.input = input;
    this.encoding = encoding;
    this.error = error;
    validate = qc.options.get(MainOptions.CHECKSTRINGS);
  }

  @Override
  public byte[] string(final InputInfo ii) throws QueryException {
    try {
      return input(ii).content();
    } catch(final IOException ex) {
      throw error.get(ii, ex);
    }
  }

  @Override
  public String toJava() throws QueryException {
    return Token.string(string(null));
  }

  @Override
  public TextInput input(final InputInfo ii) throws QueryException {
    TextInput ti = null;
    try {
      ti = new TextInput(input);
      ti.encoding(encoding).validate(validate);
      return ti;
    } catch(final IOException ex) {
      if(ti != null) try { ti.close(); } catch(final IOException ignored) { }
      throw error.get(ii, ex);
    }
  }

  @Override
  public Str materialize(final InputInfo ii) throws QueryException {
    return Str.get(string(ii));
  }

  @Override
  public String toString() {
    return Util.info(Function._FILE_READ_TEXT.args(input));
  }
}
