package org.basex.build.csv;

import org.basex.build.SingleParser;
import org.basex.core.MainOptions;
import org.basex.io.IO;

import java.io.IOException;

/**
 * This class parses files in the CSV format and converts them to XML.
 *
 * <p>The parser provides some options, which can be specified via the
 * {@link MainOptions#CSVPARSER} option.</p>
 *
 * @author BaseX Team 2005-15, BSD License
 * @author Christian Gruen
 */
public final class CsvParser extends SingleParser {
  /** CSV Parser options. */
  private final CsvParserOptions copts;

  /**
   * Constructor.
   * @param source document source
   * @param opts database options
   */
  public CsvParser(final IO source, final MainOptions opts) {
    this(source, opts, opts.get(MainOptions.CSVPARSER));
  }

  /**
   * Constructor.
   * @param source document source
   * @param opts database options
   * @param copts parser options
   */
  public CsvParser(final IO source, final MainOptions opts, final CsvParserOptions copts) {
    super(source, opts);
    this.copts = copts;
  }

  @Override
  protected void parse() throws IOException {
    new CsvBuilder(copts, builder).convert(source);
  }
}
