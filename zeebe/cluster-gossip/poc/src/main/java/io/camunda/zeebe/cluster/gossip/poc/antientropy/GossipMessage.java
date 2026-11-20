/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip.poc.antientropy;

import io.atomix.cluster.MemberId;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What the anti-entropy transport sends: a delta when pushing changes, the full state when syncing.
 * Encoded by hand with {@link DataOutputStream}; a production version would use SBE or protobuf and
 * a version header.
 */
record GossipMessage(List<RunItem> runs, List<GlobalItem> globals, List<MemberItem> members) {

  boolean isEmpty() {
    return runs.isEmpty() && globals.isEmpty() && members.isEmpty();
  }

  byte[] encode() {
    final var buffer = new ByteArrayOutputStream();
    try (final var out = new DataOutputStream(buffer)) {
      out.writeInt(runs.size());
      for (final var item : runs) {
        out.writeUTF(item.owner().id());
        writeRun(out, item.run());
      }
      out.writeInt(globals.size());
      for (final var item : globals) {
        out.writeUTF(item.name());
        out.writeBoolean(item.lastWriterWins());
        out.writeLong(item.timestamp());
        out.writeUTF(item.writer() == null ? "" : item.writer().id());
        writeBytes(out, item.bytes());
      }
      out.writeInt(members.size());
      for (final var item : members) {
        out.writeUTF(item.owner().id());
        writeRun(out, item.run());
        out.writeUTF(item.name());
        out.writeLong(item.version());
        writeBytes(out, item.bytes());
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return buffer.toByteArray();
  }

  static GossipMessage decode(final byte[] bytes) {
    try (final var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
      final var runs = new ArrayList<RunItem>();
      for (int i = in.readInt(); i > 0; i--) {
        runs.add(new RunItem(MemberId.from(in.readUTF()), readRun(in)));
      }
      final var globals = new ArrayList<GlobalItem>();
      for (int i = in.readInt(); i > 0; i--) {
        final var name = in.readUTF();
        final var lastWriterWins = in.readBoolean();
        final var timestamp = in.readLong();
        final var writer = in.readUTF();
        globals.add(
            new GlobalItem(
                name,
                lastWriterWins,
                timestamp,
                writer.isEmpty() ? null : MemberId.from(writer),
                readBytes(in)));
      }
      final var members = new ArrayList<MemberItem>();
      for (int i = in.readInt(); i > 0; i--) {
        members.add(
            new MemberItem(
                MemberId.from(in.readUTF()),
                readRun(in),
                in.readUTF(),
                in.readLong(),
                readBytes(in)));
      }
      return new GossipMessage(runs, globals, members);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void writeRun(final DataOutputStream out, final Run run) throws IOException {
    out.writeLong(run.startedAt());
    out.writeLong(run.nonce());
  }

  private static Run readRun(final DataInputStream in) throws IOException {
    return new Run(in.readLong(), in.readLong());
  }

  private static void writeBytes(final DataOutputStream out, final byte[] bytes)
      throws IOException {
    out.writeInt(bytes.length);
    out.write(bytes);
  }

  private static byte[] readBytes(final DataInputStream in) throws IOException {
    final var bytes = new byte[in.readInt()];
    in.readFully(bytes);
    return bytes;
  }

  /** Announces the current run of a member, so peers drop values of its earlier runs. */
  record RunItem(MemberId owner, Run run) {}

  record GlobalItem(
      String name,
      boolean lastWriterWins,
      long timestamp,
      @Nullable MemberId writer,
      byte[] bytes) {}

  record MemberItem(MemberId owner, Run run, String name, long version, byte[] bytes) {}
}
