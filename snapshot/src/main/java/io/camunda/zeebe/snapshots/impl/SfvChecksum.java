/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.snapshots.impl;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
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
final class SfvChecksum {

  private static final String FILE_CRC_SEPARATOR = "   ";
  private static final String FILE_CRC_SEPARATOR_REGEX = " {3}";
  private static final Pattern FILE_CRC_PATTERN =
      Pattern.compile("(.*)" + FILE_CRC_SEPARATOR_REGEX + "([0-9a-fA-F]{1,16})");
  private static final String COMBINED_VALUE_PREFIX = "; combinedValue = ";
  private static final Pattern COMBINED_VALUE_PATTERN =
      Pattern.compile(".*combinedValue\\s+=\\s+([0-9a-fA-F]{1,16})");
  private static final String SNAPSHOT_DIRECTORY_PREFIX = "; snapshot directory = ";

  private Checksum combinedChecksum;
  private final SortedMap<String, Long> checksums = new TreeMap<>();
  private String snapshotDirectoryComment;

  /**
   * creates an immutable and pre-defined checksum
   *
   * @param combinedChecksum pre-defined checksum
   */
  public SfvChecksum(long combinedChecksum) {
    this.combinedChecksum = new PreDefinedImmutableChecksum(combinedChecksum);
  }

  public SfvChecksum() {
    this.combinedChecksum = new CRC32C();
  }

  public long getCombinedValue() {
    return combinedChecksum.getValue();
  }

  public void setSnapshotDirectoryComment(String headerComment) {
    this.snapshotDirectoryComment = headerComment;
  }

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

  public void updateFromSfvFile(String... lines) {
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

  byte[] serializeSfvFileData() throws IOException {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(baos, UTF_8));
    writer.write("; This is an SFC checksum file for all files in the given directory.");
    writer.newLine();
    writer.write("; You might use cksfv or another tool to validate these files manually.");
    writer.newLine();
    writer.write("; This is an automatically created file - please do NOT modify.");
    writer.newLine();
    if (snapshotDirectoryComment != null) {
      writer.write(SNAPSHOT_DIRECTORY_PREFIX);
      writer.write(snapshotDirectoryComment);
      writer.newLine();
    }
    writer.write(COMBINED_VALUE_PREFIX);
    writer.write(Long.toHexString(combinedChecksum.getValue()));
    writer.newLine();
    writer.write("; number of files used for combined value = " + checksums.size());
    writer.newLine();
    for (Entry<String, Long> entry : checksums.entrySet()) {
      writer.write(entry.getKey());
      writer.write(FILE_CRC_SEPARATOR);
      writer.write(Long.toHexString(entry.getValue()));
      writer.newLine();
    }
    writer.flush();
    return baos.toByteArray();
  }

  private static class PreDefinedImmutableChecksum implements Checksum {

    private final long crc;

    public PreDefinedImmutableChecksum(long crc) {
      this.crc = crc;
    }

    @Override
    public void update(int b) {
      throw getUnsupportedOperationException();
    }

    @Override
    public void update(byte[] b, int off, int len) {
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
