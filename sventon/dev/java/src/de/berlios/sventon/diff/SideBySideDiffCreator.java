/*
 * ====================================================================
 * Copyright (c) 2005-2006 Sventon Project. All rights reserved.
 *
 * This software is licensed as described in the file LICENSE, which
 * you should have received as part of this distribution. The terms
 * are also available at http://sventon.berlios.de.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package de.berlios.sventon.diff;

import de.berlios.sventon.content.KeywordHandler;
import de.berlios.sventon.content.LineNumberAppender;
import static de.berlios.sventon.diff.DiffAction.*;
import static de.berlios.sventon.diff.DiffSegment.Side.LEFT;
import static de.berlios.sventon.diff.DiffSegment.Side.RIGHT;
import de.berlios.sventon.web.model.RawTextFile;
import de.berlios.sventon.web.model.SideBySideDiffRow;
import org.apache.commons.lang.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates side by side diff result instances.
 * Creates diff results by parsing diff produced by
 * {@link de.berlios.sventon.diff.DiffProducer#doNormalDiff(java.io.OutputStream)}.
 *
 * @author jesper@users.berlios.de
 */
public final class SideBySideDiffCreator {

  //TODO: Move to sventon-servlet.xml
  public static final String ENCODING = "UTF-8";

  private final List<String> leftSourceLines;
  private final List<String> rightSourceLines;

  /**
   * Constructor.
   *
   * @param fromFile
   * @param fromKeywordHandler
   * @param toFile
   * @param toKeywordHandler
   * @throws IOException
   */
  public SideBySideDiffCreator(final RawTextFile fromFile, final KeywordHandler fromKeywordHandler,
                               final RawTextFile toFile, final KeywordHandler toKeywordHandler)
      throws IOException {

    final LineNumberAppender lineNumberAppender = new LineNumberAppender();
    lineNumberAppender.setEmbedStart("<span class=\"sventonLineNo\">");
    lineNumberAppender.setEmbedEnd(":&nbsp;</span>");
    lineNumberAppender.setPadding(5);

    final String leftString = replaceLeadingSpaces(appendKeywords(fromKeywordHandler, fromFile.getContent(), ENCODING));
    final String rightString = replaceLeadingSpaces(appendKeywords(toKeywordHandler, toFile.getContent(), ENCODING));

    leftSourceLines = toLinesList(lineNumberAppender.appendTo(leftString));
    rightSourceLines = toLinesList(lineNumberAppender.appendTo(rightString));

  }

  /**
   * Parses the diff result and creates a side by side object instance as representation.
   *
   * @param diffResult The <tt>NORMAL</tt> diff result string.
   * @return The side-by-side representation.
   */
  public List<SideBySideDiffRow> createFromDiffResult(final String diffResult) {
    final List<DiffSegment> diffSegments = DiffResultParser.parseNormalDiffResult(diffResult);
    final List<SourceLine> leftLinesList = process(LEFT, leftSourceLines, diffSegments);
    final List<SourceLine> rightLinesList = process(RIGHT, rightSourceLines, diffSegments);
    assertEqualListSize(diffResult, leftLinesList, rightLinesList);

    final List<SideBySideDiffRow> diff = new ArrayList<SideBySideDiffRow>();

    for (int i = 0; i < leftLinesList.size(); i++) {
      diff.add(new SideBySideDiffRow(i + 1, leftLinesList.get(i), rightLinesList.get(i)));
    }
    return diff;
  }

  /**
   * Replaces leading spaces with the web safe entity <code>&nbsp;</code>.
   * TODO: Replace this hack!
   *
   * @param content The source input lines
   * @return Lines with web safe indentation
   */
  protected static String replaceLeadingSpaces(final String content) throws IOException {
    final String br = System.getProperty("line.separator");
    final BufferedReader reader = new BufferedReader(new StringReader(content));
    final StringBuilder sb = new StringBuilder();
    String tempLine;
    while ((tempLine = reader.readLine()) != null) {
      if (StringUtils.isEmpty(tempLine.trim())) {
        sb.append(tempLine);
      } else {
        final StringBuffer line = new StringBuffer(tempLine);
        int index = 0;
        while (line.charAt(index) == ' ') {
          line.replace(index, index + 1, "&nbsp;");
          index += 6;
        }
        sb.append(line);
      }
      sb.append(br);
    }
    return sb.toString();
  }


  /**
   * Appends keywords if KeywordHandler is not null.
   *
   * @param keywordHandler Handler
   * @param content        Content to apply keywords to
   * @param encoding       Encoding to use.
   * @return New content
   * @throws java.io.UnsupportedEncodingException
   *          if UTF-8 is not supported
   */
  private String appendKeywords(final KeywordHandler keywordHandler, final String content, final String encoding)
      throws UnsupportedEncodingException {

    String newContent = content;
    if (keywordHandler != null) {
      newContent = keywordHandler.substitute(content, encoding);
    }
    return newContent;
  }

  /**
   * Asserts that given lists have the same number of elements.
   *
   * @param diffResult Diff result string
   * @param firstList  First list
   * @param secondList Second list
   * @throws RuntimeException if list size does not match.
   */
  private void assertEqualListSize(final String diffResult, final List<?> firstList, final List<?> secondList) {
    if (firstList.size() != secondList.size()) {
      final StringBuilder sb = new StringBuilder("Error while applying diff result!");
      sb.append("\nLine diff count: ");
      sb.append(firstList.size() - secondList.size());
      sb.append("\nDiffresult:\n");
      sb.append(diffResult);
      sb.append("\nLeft:\n");
      sb.append(firstList);
      sb.append("\nRight:\n");
      sb.append(secondList);
      throw new RuntimeException(sb.toString());
    }
  }

  /**
   * Reads given input string line by line and creates a list of lines.
   *
   * @param string String to read
   * @return List of lines (strings)
   * @throws IOException if unable to read string
   */
  private List<String> toLinesList(final String string) throws IOException {
    final List<String> lines = new ArrayList<String>();
    final BufferedReader reader = new BufferedReader(new StringReader(string));
    String tempLine;
    while ((tempLine = reader.readLine()) != null) {
      lines.add(tempLine);
    }
    return lines;
  }

  /**
   * Process given list of source lines.
   *
   * @param side         Side to process
   * @param sourceLines  Sourcelines
   * @param diffSegments DiffSegments
   * @return List of processed lines.
   */
  private List<SourceLine> process(final DiffSegment.Side side, final List<String> sourceLines, final List<DiffSegment> diffSegments) {
    final List<SourceLine> resultLines = new ArrayList<SourceLine>();
    for (String tempLine : sourceLines) {
      resultLines.add(new SourceLine(UNCHANGED, tempLine));
    }

    int offset = 0;
    for (DiffSegment diffSegment : diffSegments) {
      if (ADDED == diffSegment.getAction()) {
        offset = applyAdded(side, diffSegment, resultLines, offset);
      } else if (DELETED == diffSegment.getAction()) {
        offset = applyDeleted(side, diffSegment, resultLines, offset);
      } else if (CHANGED == diffSegment.getAction()) {
        offset = applyChanged(side, diffSegment, resultLines, offset);
      }
    }
    return resultLines;
  }

  /**
   * Applies the diff action CHANGED to given list of sourcelines.
   *
   * @param side        Left or right
   * @param diffSegment DiffSegment
   * @param resultLines The sourcelines to update
   * @param offset      Row offset
   * @return Updated row offset
   */
  private int applyChanged(final DiffSegment.Side side, final DiffSegment diffSegment, final List<SourceLine> resultLines, final int offset) {
    int newOffset = offset;
    int rowIndex;
    int start = diffSegment.getLineIntervalStart(side.opposite());
    int end = diffSegment.getLineIntervalEnd(side.opposite());

    int changedLines = 0;
    for (int i = start; i <= end; i++) {
      resultLines.set(i - 1 + newOffset, new SourceLine(CHANGED, resultLines.get(i - 1 + newOffset).getLine()));
      changedLines++;
    }

    start = diffSegment.getLineIntervalStart(side);
    end = diffSegment.getLineIntervalEnd(side);
    rowIndex = diffSegment.getLineIntervalEnd(side.opposite());

    int addedLines = 0;
    for (int i = start + changedLines; i <= end; i++) {
      resultLines.add(rowIndex + newOffset, new SourceLine(CHANGED, ""));
      addedLines++;
      if (side == DiffSegment.Side.LEFT) {
        changedLines++;
      }
    }
    newOffset += addedLines;
    return newOffset;
  }

  /**
   * Applies the diff action DELETED to given list of sourcelines.
   *
   * @param side        Left or right
   * @param diffSegment DiffSegment
   * @param resultLines The sourcelines to update
   * @param offset      Row offset
   * @return Updated row offset
   */
  private int applyDeleted(final DiffSegment.Side side, final DiffSegment diffSegment, final List<SourceLine> resultLines, final int offset) {
    int deletedLines = 0;
    for (int i = diffSegment.getLineIntervalStart(LEFT); i <= diffSegment.getLineIntervalEnd(LEFT); i++) {
      if (side == LEFT) {
        resultLines.set(i - 1, new SourceLine(DELETED, resultLines.get(i - 1).getLine()));
      } else {
        resultLines.add(i - 1, new SourceLine(DELETED, ""));
        deletedLines++;
      }
    }
    return offset + deletedLines;
  }

  /**
   * Applies the diff action ADDED to given list of sourcelines.
   *
   * @param side        Left or right
   * @param diffSegment DiffSegment
   * @param resultLines The sourcelines to update
   * @param offset      Row offset
   * @return Updated row offset
   */
  private int applyAdded(final DiffSegment.Side side, final DiffSegment diffSegment, final List<SourceLine> resultLines, final int offset) {
    int newOffset = offset;
    switch (side) {
      case LEFT:
        int addedLines = 0;
        int startLine = diffSegment.getLineIntervalStart(side) + newOffset;
        for (int i = diffSegment.getLineIntervalStart(side.opposite()); i <= diffSegment.getLineIntervalEnd(side.opposite()); i++)
        {
          resultLines.add(startLine++ - 1, new SourceLine(ADDED, ""));
          addedLines++;
        }
        newOffset += addedLines;
        break;
      case RIGHT:
        for (int i = diffSegment.getLineIntervalStart(side); i <= diffSegment.getLineIntervalEnd(side); i++) {
          resultLines.set(i - 1 + newOffset, new SourceLine(ADDED, resultLines.get(i - 1 + newOffset).getLine()));
        }
    }
    return newOffset;
  }
}