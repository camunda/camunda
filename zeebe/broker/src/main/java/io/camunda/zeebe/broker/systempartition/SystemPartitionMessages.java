/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.systempartition;

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.LeaderConfiguration;
import io.atomix.raft.partition.systempartition.SystemPartitionRecovery.MemberReport;
import io.atomix.raft.storage.serializer.MetaStoreSerializer;
import io.atomix.raft.storage.system.Configuration;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import org.jspecify.annotations.Nullable;

/**
 * The messages that brokers exchange about the system partition, outside of Raft: the probe of the
 * start (5.4.2), the reports of a recovery (5.6.3, step 1) and the read of the leader's
 * configuration (R3) through another broker. Configurations are encoded like the {@code .conf}
 * file.
 */
final class SystemPartitionMessages {

  static final String PROBE_SUBJECT = "system-partition-probe";
  static final String REPORT_SUBJECT = "system-partition-report";
  static final String READ_LEADER_SUBJECT = "system-partition-read-leader";

  private SystemPartitionMessages() {}

  static byte[] encodeProbe(final ProbeAnswer answer) {
    return write(
        out -> {
          out.writeBoolean(answer.hasConfiguration());
          out.writeLong(answer.index());
          out.writeLong(answer.term());
          out.writeUTF(answer.hash());
          out.writeUTF(answer.origin().name());
        });
  }

  static ProbeAnswer decodeProbe(final byte[] bytes) {
    return read(
        bytes,
        in ->
            new ProbeAnswer(
                in.readBoolean(),
                in.readLong(),
                in.readLong(),
                in.readUTF(),
                Origin.valueOf(in.readUTF())));
  }

  static byte[] encodeReport(final MemberReport report) {
    return write(
        out -> {
          out.writeUTF(report.memberId().id());
          writeConfiguration(out, report.configuration());
          out.writeLong(report.incarnation());
          out.writeLong(report.lastLogTerm());
          out.writeLong(report.lastLogIndex());
          out.writeLong(report.commitIndex());
        });
  }

  static MemberReport decodeReport(final byte[] bytes) {
    return read(
        bytes,
        in ->
            new MemberReport(
                MemberId.from(in.readUTF()),
                readConfiguration(in),
                in.readLong(),
                in.readLong(),
                in.readLong(),
                in.readLong(),
                null,
                0));
  }

  static byte[] encodeLeaderConfiguration(final LeaderConfiguration read) {
    return write(
        out -> {
          out.writeUTF(read.leader().id());
          out.writeBoolean(read.committed());
          writeConfiguration(out, read.configuration());
        });
  }

  static LeaderConfiguration decodeLeaderConfiguration(final byte[] bytes) {
    return read(
        bytes,
        in -> {
          final var leader = MemberId.from(in.readUTF());
          final boolean committed = in.readBoolean();
          return new LeaderConfiguration(leader, readConfiguration(in), committed);
        });
  }

  private static void writeConfiguration(
      final DataOutputStream out, final @Nullable Configuration configuration) throws IOException {
    if (configuration == null) {
      out.writeInt(0);
      return;
    }
    final var buffer = new MetaStoreSerializer().writeConfiguration(configuration);
    buffer.position(0);
    final var bytes = new byte[buffer.capacity()];
    buffer.get(bytes);
    out.writeInt(bytes.length);
    out.write(bytes);
  }

  private static @Nullable Configuration readConfiguration(final DataInputStream in)
      throws IOException {
    final int length = in.readInt();
    if (length == 0) {
      return null;
    }
    final var bytes = in.readNBytes(length);
    return new MetaStoreSerializer().readConfiguration(ByteBuffer.wrap(bytes));
  }

  private static byte[] write(final Writer writer) {
    final var bytes = new ByteArrayOutputStream();
    try (final var out = new DataOutputStream(bytes)) {
      writer.write(out);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return bytes.toByteArray();
  }

  private static <T> T read(final byte[] bytes, final Reader<T> reader) {
    try (final var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
      return reader.read(in);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Whether the broker ran before with a version without the system partition (5.4.5). */
  enum Origin {
    NEW,
    EXISTING
  }

  /**
   * The answer to the probe (5.4.2).
   *
   * @param hasConfiguration false for "no data"
   * @param index the stored configuration index
   * @param term the stored configuration term
   * @param hash the hash of the voter policy and the initial configuration
   * @param origin the origin of the broker
   */
  record ProbeAnswer(boolean hasConfiguration, long index, long term, String hash, Origin origin) {}

  @FunctionalInterface
  private interface Writer {
    void write(DataOutputStream out) throws IOException;
  }

  @FunctionalInterface
  private interface Reader<T> {
    T read(DataInputStream in) throws IOException;
  }
}
