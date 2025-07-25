/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.impl;

import io.camunda.zeebe.snapshots.ImmutableChecksumsSFV;
import io.camunda.zeebe.snapshots.MutableChecksumsSFV;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map.Entry;
import java.util.Objects;
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
public final class SfvChecksumImpl implements MutableChecksumsSFV {

  private static final String SFV_HEADER =
      """
; This is an SFC checksum file for all files in the given directory.
; You might use cksfv or another tool to validate these files manually.
; This is an automatically created file - please do NOT modify.
""";
  private static final String FORMAT_SNAPSHOT_DIRECTORY_LINE = "; snapshot directory = %s\n";
  private static final String FORMAT_FILE_CRC_LINE = "%s   %s\n";
  private static final String FILE_CRC_SEPARATOR_REGEX = " {3}";
  private static final Pattern FILE_CRC_PATTERN =
      Pattern.compile("(.*)" + FILE_CRC_SEPARATOR_REGEX + "([0-9a-fA-F]{1,16})");
  private final SortedMap<String, Long> checksums = new TreeMap<>();
  private String snapshotDirectoryComment;

  public SfvChecksumImpl() {}

  @Override
  public void write(final OutputStream stream) throws IOException {
    final var writer = new PrintWriter(stream);
    writer.print(SFV_HEADER);
    if (snapshotDirectoryComment != null) {
      writer.printf(FORMAT_SNAPSHOT_DIRECTORY_LINE, snapshotDirectoryComment);
    }
    for (final Entry<String, Long> entry : checksums.entrySet()) {
      writer.printf(FORMAT_FILE_CRC_LINE, entry.getKey(), Long.toHexString(entry.getValue()));
    }
    writer.flush();

    if (writer.checkError()) {
      throw new IOException("Expected to write the SFV Checksum, but failed during writing.");
    }
  }

  @Override
  public SortedMap<String, Long> getChecksums() {
    return checksums;
  }

  @Override
  public boolean sameChecksums(final ImmutableChecksumsSFV o) {
    if (this == o) {
      return true;
    }
    if (o == null) {
      return false;
    }
    return Objects.equals(checksums, o.getChecksums());
  }

  @Override
  public long getCombinedChecksum() {
    final var combinedChecksum = new CRC32C();
    for (final var entry : checksums.entrySet()) {
      combinedChecksum.update(entry.getKey().getBytes(StandardCharsets.UTF_8));
      final int upper = (int) (entry.getValue() >> 32);
      final int lower = (int) entry.getValue().longValue();
      combinedChecksum.update(upper);
      combinedChecksum.update(lower);
    }
    return combinedChecksum.getValue();
  }

  @Override
  public String toString() {
    return "SfvChecksumImpl{" + ", checksums=" + checksums + '}';
  }

  public void setSnapshotDirectoryComment(final String headerComment) {
    snapshotDirectoryComment = headerComment;
  }

  @Override
  public void updateFromFile(final Path filePath) throws IOException {
    final String fileName = filePath.getFileName().toString();

    final Checksum checksum = new CRC32C();
    final ByteBuffer readBuffer = ByteBuffer.allocate(IoUtil.BLOCK_SIZE);
    try (final FileChannel channel = FileChannel.open(filePath, StandardOpenOption.READ)) {
      readBuffer.clear();
      while (channel.read(readBuffer) > 0) {
        readBuffer.flip();
        checksum.update(readBuffer);
        readBuffer.clear();
      }
    }
    checksums.put(fileName, checksum.getValue());
  }

  @Override
  public void updateFromBytes(final String fileName, final byte[] bytes) {
    final Checksum checksum = new CRC32C();
    checksum.update(bytes);
    checksums.put(fileName, checksum.getValue());
  }

  @Override
  public void updateFromSfvFile(final String... lines) {
    for (String line : lines) {
      line = line.trim();

      if (line.startsWith(";")) {
        continue;
      }

      final Matcher matcher = FILE_CRC_PATTERN.matcher(line);
      if (matcher.find()) {
        final Long crc = Long.parseLong(matcher.group(2), 16);
        final String fileName = matcher.group(1).trim();
        checksums.put(fileName, crc);
      }
    }
  }

  @Override
  public void updateFromChecksum(final Path filePath, final long checksum) {
    final String fileName = filePath.getFileName().toString();
    checksums.put(fileName, checksum);
  }
}
