/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector.swim;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The periodic report of a system partition broker about its SWIM view of the cluster. It is
 * cumulative, so a lost or late report is superseded by the next one and none is retried. Reports
 * are also the brokers' heartbeats: a broker whose reports stop arriving is silent, and its claims
 * stop counting.
 *
 * @param observerId the reporting broker
 * @param epoch identifies the run of the observer, so a restarted observer, whose sequence starts
 *     over, is not mistaken for an old report
 * @param sequence orders the reports of one run of the observer
 * @param observations what the observer sees of every broker of the cluster
 */
record SwimReport(String observerId, long epoch, long sequence, List<Observation> observations) {
  static final String SUBJECT = "system-partition-swim-report";

  SwimReport {
    observations = List.copyOf(observations);
  }

  /** Returns what the observer sees of the broker, UNKNOWN if the report does not say. */
  Observation observationOf(final String memberId) {
    return observations.stream()
        .filter(observation -> observation.memberId().equals(memberId))
        .findFirst()
        .orElseGet(() -> Observation.unknown(memberId));
  }

  /**
   * Returns whether the observer sees at least one broker other than itself. An observer that sees
   * nobody else is most likely cut off or struggling itself, so its claim that everyone else is out
   * of sight says more about it than about them and is ignored.
   */
  boolean isCredible() {
    return observations.stream()
        .anyMatch(
            observation ->
                !observation.memberId().equals(observerId)
                    && observation.state() == ObservedState.ALIVE);
  }

  byte[] encode() {
    try (final var bytes = new ByteArrayOutputStream();
        final var out = new DataOutputStream(bytes)) {
      out.writeUTF(observerId);
      out.writeLong(epoch);
      out.writeLong(sequence);
      out.writeInt(observations.size());
      for (final var observation : observations) {
        out.writeUTF(observation.memberId());
        out.writeByte(observation.state().code());
        out.writeLong(observation.unreachableMillis());
      }
      out.flush();
      return bytes.toByteArray();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static SwimReport decode(final byte[] bytes) {
    try (final var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
      final var observerId = in.readUTF();
      final var epoch = in.readLong();
      final var sequence = in.readLong();
      final var count = in.readInt();
      final var observations = new ArrayList<Observation>(count);
      for (int i = 0; i < count; i++) {
        observations.add(
            new Observation(in.readUTF(), ObservedState.of(in.readByte()), in.readLong()));
      }
      return new SwimReport(observerId, epoch, sequence, observations);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
