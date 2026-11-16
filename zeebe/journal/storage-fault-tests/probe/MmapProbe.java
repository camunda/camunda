/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Calibration for LazyFS with memory-mapped files, mirroring how Zeebe writes its journal.
 *
 * <p>write <file>: create a 64 KiB file filled with zeros and fsynced (like the FILL allocator),
 * map it, write "FORCED" at 0 and force(), write "UNFORCED" at 8192 without force, then block until
 * killed. read <file>: print what is at both offsets.
 */
public class MmapProbe {
  public static void main(String[] args) throws Exception {
    final Path file = Path.of(args[1]);
    if (args[0].equals("write")) {
      try (var raf = new RandomAccessFile(file.toFile(), "rw");
          var ch = raf.getChannel()) {
        final var zeros = ByteBuffer.allocate(64 * 1024);
        ch.write(zeros, 0);
        ch.force(true);
      }
      try (var ch = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
        final MappedByteBuffer map = ch.map(FileChannel.MapMode.READ_WRITE, 0, 64 * 1024);
        map.put(0, "FORCED".getBytes(StandardCharsets.US_ASCII));
        map.force();
        map.put(8192, "UNFORCED".getBytes(StandardCharsets.US_ASCII));
        System.out.println("written; waiting to be killed");
        System.out.flush();
        Thread.sleep(Long.MAX_VALUE);
      }
    } else {
      try (var ch = FileChannel.open(file, StandardOpenOption.READ)) {
        final var buf = ByteBuffer.allocate(64 * 1024);
        ch.read(buf, 0);
        System.out.println("size=" + ch.size());
        System.out.println("offset 0    : '" + text(buf, 0, 6) + "'");
        System.out.println("offset 8192 : '" + text(buf, 8192, 8) + "'");
      }
    }
  }

  private static String text(ByteBuffer buf, int off, int len) {
    final var b = new byte[len];
    buf.get(off, b);
    return new String(b, StandardCharsets.US_ASCII).replace('\0', '.');
  }
}
