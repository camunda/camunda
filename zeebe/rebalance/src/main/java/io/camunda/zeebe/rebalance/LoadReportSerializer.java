/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import com.google.protobuf.InvalidProtocolBufferException;
import io.camunda.zeebe.dynamic.config.serializer.DecodingFailed;
import io.camunda.zeebe.rebalance.protocol.Rebalance;
import java.util.EnumMap;
import java.util.Map;

final class LoadReportSerializer {

  private LoadReportSerializer() {}

  static byte[] encodeTotals(final LoadTotals totals) {
    final var builder = Rebalance.LoadTotals.newBuilder().setIncarnation(totals.incarnation());
    if (totals.cpuTimeNanos() >= 0 && totals.cpus() > 0) {
      builder.setCpuTimeNanos(totals.cpuTimeNanos()).setCpus(totals.cpus());
    }
    totals
        .totals()
        .forEach(
            (measure, count) ->
                builder.addTotals(
                    Rebalance.LoadTotals.Total.newBuilder()
                        .setMeasure(encodeMeasure(measure))
                        .setCount(count)));
    return builder.build().toByteArray();
  }

  /**
   * Measures this version does not know are dropped, so the totals look incomplete. A broker that
   * does not report its CPU leaves it unknown.
   */
  static LoadTotals decodeTotals(final byte[] encoded) {
    final Rebalance.LoadTotals decoded;
    try {
      decoded = Rebalance.LoadTotals.parseFrom(encoded);
    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    }
    final Map<LoadMeasure, Long> totals = new EnumMap<>(LoadMeasure.class);
    for (final var total : decoded.getTotalsList()) {
      switch (total.getMeasure()) {
        case ROOT_PROCESS_INSTANCES ->
            totals.put(LoadMeasure.ROOT_PROCESS_INSTANCES, total.getCount());
        case PROCESSED_COMMANDS -> totals.put(LoadMeasure.PROCESSED_COMMANDS, total.getCount());
        default -> {}
      }
    }
    return decoded.getCpus() > 0
        ? new LoadTotals(
            decoded.getIncarnation(), totals, decoded.getCpuTimeNanos(), decoded.getCpus())
        : new LoadTotals(decoded.getIncarnation(), totals, -1, 0);
  }

  private static Rebalance.LoadTotals.LoadMeasure encodeMeasure(final LoadMeasure measure) {
    return switch (measure) {
      case ROOT_PROCESS_INSTANCES -> Rebalance.LoadTotals.LoadMeasure.ROOT_PROCESS_INSTANCES;
      case PROCESSED_COMMANDS -> Rebalance.LoadTotals.LoadMeasure.PROCESSED_COMMANDS;
    };
  }
}
