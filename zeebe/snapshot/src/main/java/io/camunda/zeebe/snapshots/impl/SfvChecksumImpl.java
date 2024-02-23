/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.snapshots.impl;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.camunda.zeebe.snapshots.MutableChecksumsSFV;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32C;
import java.util.zip.Checksum;
import org.agrona.IoUtil;

/**
 * Supports building individual CRCs compatible with SFV file format and also supports backward
 * compatibility with 'combinedChecksum' field.
 * https://en.wikipedia.org/wiki/Simple_file_verification
 */
final class SfvChecksumImpl implements MutableChecksumsSFV {

  public static final String FORMAT_NUMBER_OF_FILES_LINE =
      "; number of files used for combined value = %d\n";
  private static final String SFV_HEADER =
      """
; This is an SFC checksum file for all files in the given directory.
; You might use cksfv or another tool to validate these files manually.
; This is an automatically created file - please do NOT modify.
""";
  private static final String FORMAT_SNAPSHOT_DIRECTORY_LINE = "; snapshot directory = %s\n";
  private static final String FORMAT_FILE_CRC_LINE = "%s   %s\n";
  private static final String FORMAT_COMBINED_VALUE_LINE = "; combinedValue = %s\n";
  private static final String FILE_CRC_SEPARATOR_REGEX = " {3}";
  private static final Pattern FILE_CRC_PATTERN =
      Pattern.compile("(.*)" + FILE_CRC_SEPARATOR_REGEX + "([0-9a-fA-F]{1,16})");
  private static final Pattern COMBINED_VALUE_PATTERN =
      Pattern.compile(".*combinedValue\\s+=\\s+([0-9a-fA-F]{1,16})");
  private Checksum combinedChecksum;
  private final SortedMap<String, Long> checksums = new TreeMap<>();
  private String snapshotDirectoryComment;

  /**
   * creates an immutable and pre-defined checksum
   *
   * @param combinedChecksum pre-defined checksum
   */
  public SfvChecksumImpl(final long combinedChecksum) {
    this.combinedChecksum = new PreDefinedImmutableChecksum(combinedChecksum);
  }

  public SfvChecksumImpl() {
    combinedChecksum = new CRC32C();
  }

  @Override
  public long getCombinedValue() {
    return combinedChecksum.getValue();
  }

  @Override
  public void write(final OutputStream stream) throws IOException {
    final var writer = new PrintWriter(stream);
    writer.print(SFV_HEADER);
    if (snapshotDirectoryComment != null) {
      writer.printf(FORMAT_SNAPSHOT_DIRECTORY_LINE, snapshotDirectoryComment);
    }
    writer.printf(FORMAT_COMBINED_VALUE_LINE, Long.toHexString(combinedChecksum.getValue()));
    writer.printf(FORMAT_NUMBER_OF_FILES_LINE, checksums.size());

    for (final Entry<String, Long> entry : checksums.entrySet()) {
      writer.printf(FORMAT_FILE_CRC_LINE, entry.getKey(), Long.toHexString(entry.getValue()));
    }
    writer.flush();

    if (writer.checkError()) {
      throw new IOException("Expected to write the SFV Checksum, but failed during writing.");
    }
  }

  public void setSnapshotDirectoryComment(final String headerComment) {
    snapshotDirectoryComment = headerComment;
  }

  @Override
  public String toString() {
    return "SfvChecksum{"
        + "combinedChecksum="
        + combinedChecksum.getValue()
        + ", checksums="
        + checksums
        + '}';
  }

  @Override
  public void updateFromFile(final Path filePath) throws IOException {
    final String fileName = filePath.getFileName().toString();
    final byte[] chunkId = fileName.getBytes(UTF_8);
    combinedChecksum.update(chunkId);

    final Checksum checksum = new CRC32C();
    final ByteBuffer readBuffer = ByteBuffer.allocate(IoUtil.BLOCK_SIZE);
    try (final FileChannel channel = FileChannel.open(filePath, StandardOpenOption.READ)) {
      readBuffer.clear();
      while (channel.read(readBuffer) > 0) {
        readBuffer.flip();
        combinedChecksum.update(readBuffer);
        readBuffer.flip();
        checksum.update(readBuffer);
        readBuffer.clear();
      }
    }
    checksums.put(fileName, checksum.getValue());
  }

  @Override
  public void updateFromBytes(final String fileName, final byte[] bytes) {
    combinedChecksum.update(fileName.getBytes(UTF_8));
    final Checksum checksum = new CRC32C();
    checksum.update(bytes);
    combinedChecksum.update(bytes);
    checksums.put(fileName, checksum.getValue());
  }

  @Override
  public void updateFromSfvFile(final String... lines) {
    for (String line : lines) {
      line = line.trim();
      if (line.startsWith(";")) {
        final Matcher matcher = COMBINED_VALUE_PATTERN.matcher(line);
        if (matcher.find()) {
          final String hexString = matcher.group(1);
          final long crc = Long.parseLong(hexString, 16);
          combinedChecksum = new PreDefinedImmutableChecksum(crc);
        }
      } else {
        final Matcher matcher = FILE_CRC_PATTERN.matcher(line);
        if (matcher.find()) {
          final Long crc = Long.parseLong(matcher.group(2), 16);
          final String fileName = matcher.group(1).trim();
          checksums.put(fileName, crc);
        }
      }
    }
  }

  private static class PreDefinedImmutableChecksum implements Checksum {

    private final long crc;

    public PreDefinedImmutableChecksum(final long crc) {
      this.crc = crc;
    }

    @Override
    public void update(final int b) {
      throw getUnsupportedOperationException();
    }

    @Override
    public void update(final byte[] b, final int off, final int len) {
      throw getUnsupportedOperationException();
    }

    @Override
    public long getValue() {
      return crc;
    }

    @Override
    public void reset() {
      throw getUnsupportedOperationException();
    }

    private static UnsupportedOperationException getUnsupportedOperationException() {
      return new UnsupportedOperationException("This is an immutable checksum.");
    }
  }
}
