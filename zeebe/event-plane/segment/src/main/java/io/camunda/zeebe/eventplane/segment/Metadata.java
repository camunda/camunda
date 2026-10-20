/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.segment;

import static io.camunda.zeebe.eventplane.segment.Entry.NO_POSITION;

import io.camunda.zeebe.eventplane.segment.InvalidSegmentException.InvalidMetadata;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/** The metadata entries this reader knows: the segment's log, and where its frames start. */
public record Metadata(Identity identity, List<Frame> frames) {

  private static final int IDENTITY = 1;
  private static final int FRAME_INDEX = 2;
  private static final int DIRECTORY_ENTRY_LENGTH = 2 + 4 + 4;
  private static final int FRAME_LENGTH = 16;

  public Metadata {
    frames = List.copyOf(frames);
    if (frames.isEmpty()) {
      throw new InvalidMetadata("Expected a segment to have at least one frame, but got none");
    }
    if (frames.getFirst().offset() != 0) {
      throw new InvalidMetadata(
          "Expected a segment's first frame at offset 0, but got " + frames.getFirst().offset());
    }
    for (int i = 1; i < frames.size(); i++) {
      if (frames.get(i).offset() <= frames.get(i - 1).offset()) {
        throw new InvalidMetadata(
            "Expected frame %d's offset to be after %d, but got %d"
                .formatted(i, frames.get(i - 1).offset(), frames.get(i).offset()));
      }
    }
    long lastPosition = NO_POSITION;
    for (int i = 0; i < frames.size(); i++) {
      final long position = frames.get(i).firstPosition();
      if (position != NO_POSITION && position < 1) {
        throw new InvalidMetadata(
            "Expected frame %d's first position to be %d or at least 1, but got %d"
                .formatted(i, NO_POSITION, position));
      }
      if (position != NO_POSITION && position <= lastPosition) {
        throw new InvalidMetadata(
            "Expected frame %d's first position to be after %d, but got %d"
                .formatted(i, lastPosition, position));
      }
      if (position != NO_POSITION) {
        lastPosition = position;
      }
    }
  }

  /**
   * Where a reader that has every record up to {@code position} starts in the object: at the last
   * frame whose first position is at most {@code position + 1}, or at the body's start if there is
   * none. It then skips the records up to {@code position}.
   */
  public long startAfter(long position) {
    long start = 0;
    for (var frame : frames) {
      if (frame.firstPosition() != NO_POSITION && frame.firstPosition() <= position + 1) {
        start = frame.offset();
      }
    }
    return start;
  }

  byte[] encode() {
    final var index =
        ByteBuffer.allocate(frames.size() * FRAME_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
    frames.forEach(frame -> index.putLong(frame.firstPosition()).putLong(frame.offset()));
    final var entries = new TreeMap<Integer, byte[]>();
    entries.put(IDENTITY, identity.encode());
    entries.put(FRAME_INDEX, index.array());
    return encode(entries);
  }

  /** Writes metadata entries, by key, and the directory after them. */
  static byte[] encode(SortedMap<Integer, byte[]> entries) {
    final int values = entries.values().stream().mapToInt(value -> value.length).sum();
    final var metadata =
        ByteBuffer.allocate(values + entries.size() * DIRECTORY_ENTRY_LENGTH + 4)
            .order(ByteOrder.LITTLE_ENDIAN);
    entries.values().forEach(metadata::put);
    int offset = 0;
    for (var entry : entries.entrySet()) {
      metadata.putShort(entry.getKey().shortValue()).putInt(offset).putInt(entry.getValue().length);
      offset += entry.getValue().length;
    }
    metadata.putInt(entries.size());
    return metadata.array();
  }

  /**
   * Reads the metadata from {@code tail}, the object's bytes from {@code tailStart} to its end. It
   * must start at or before the metadata, which starts at the footer's body length; if it doesn't,
   * the reader reads the tail again from there. Skips metadata entries this reader doesn't know.
   */
  public static Metadata read(ByteBuffer tail, long tailStart, Footer footer)
      throws TailTooShortException {
    if (footer.bodyLength() < tailStart) {
      throw new TailTooShortException(tailStart, footer.bodyLength());
    }
    // Compared before any arithmetic on the body length, which a damaged footer can set to
    // anything.
    final long footerStart = tailStart + tail.remaining() - Footer.LENGTH;
    if (footer.bodyLength() > footerStart - 4) {
      throw new InvalidMetadata(
          "Expected the body to end by byte %d, 4 bytes before the footer, but it ends at %d"
              .formatted(footerStart - 4, footer.bodyLength()));
    }
    final int start = tail.position() + (int) (footer.bodyLength() - tailStart);
    final int metadataLength = (int) (footerStart - footer.bodyLength());
    final var metadata = tail.slice(start, metadataLength).order(ByteOrder.LITTLE_ENDIAN);
    final int count = metadata.getInt(metadata.limit() - 4);
    final int maxCount = (metadata.limit() - 4) / DIRECTORY_ENTRY_LENGTH;
    if (count < 0 || count > maxCount) {
      throw new InvalidMetadata(
          "Expected 0 to %d metadata entries in %d bytes of metadata, but the directory lists %d"
              .formatted(maxCount, metadata.limit(), Integer.toUnsignedLong(count)));
    }
    final int directory = metadata.limit() - 4 - count * DIRECTORY_ENTRY_LENGTH;
    final var values = new HashMap<Integer, ByteBuffer>();
    for (int i = 0; i < count; i++) {
      final int at = directory + i * DIRECTORY_ENTRY_LENGTH;
      final int key = Short.toUnsignedInt(metadata.getShort(at));
      final long offset = Integer.toUnsignedLong(metadata.getInt(at + 2));
      final long length = Integer.toUnsignedLong(metadata.getInt(at + 6));
      if (offset + length > directory) {
        throw new InvalidMetadata(
            ("Expected metadata entry %d to end by byte %d, where the directory starts, but it"
                    + " runs from %d to %d")
                .formatted(key, directory, offset, offset + length));
      }
      final var value = metadata.slice((int) offset, (int) length).order(ByteOrder.LITTLE_ENDIAN);
      if (values.put(key, value) != null) {
        throw new InvalidMetadata(
            "Expected metadata entry %d once in the directory, but got it twice".formatted(key));
      }
    }
    final var identity = values.get(IDENTITY);
    if (identity == null) {
      throw new InvalidMetadata(
          "Expected metadata entry %d, the identity, but got keys %s"
              .formatted(IDENTITY, new TreeMap<>(values).keySet()));
    }
    final var frameIndex = values.get(FRAME_INDEX);
    if (frameIndex == null) {
      throw new InvalidMetadata(
          "Expected metadata entry %d, the frame index, but got keys %s"
              .formatted(FRAME_INDEX, new TreeMap<>(values).keySet()));
    }
    if (frameIndex.remaining() % FRAME_LENGTH != 0) {
      throw new InvalidMetadata(
          "Expected a frame index of %d bytes per frame, but got %d bytes"
              .formatted(FRAME_LENGTH, frameIndex.remaining()));
    }
    final var frames = new ArrayList<Frame>();
    while (frameIndex.hasRemaining()) {
      final var frame = new Frame(frameIndex.getLong(), frameIndex.getLong());
      if (frame.offset() >= footer.bodyLength()) {
        throw new InvalidMetadata(
            "Expected frame %d to start before the body's end at %d, but it starts at %d"
                .formatted(frames.size(), footer.bodyLength(), frame.offset()));
      }
      frames.add(frame);
    }
    return new Metadata(Identity.decode(identity), frames);
  }
}
