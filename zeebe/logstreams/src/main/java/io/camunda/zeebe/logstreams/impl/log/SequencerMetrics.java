/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.log;

import static io.camunda.zeebe.logstreams.impl.log.SequencerMetrics.SequencerMetricsDoc.BATCH_LENGTH_BYTES;
import static io.camunda.zeebe.logstreams.impl.log.SequencerMetrics.SequencerMetricsDoc.BATCH_SIZE;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.MeterRegistry;

final class SequencerMetrics {
  private final DistributionSummary batchSize;
  private final DistributionSummary batchLengthBytes;

  SequencerMetrics(final MeterRegistry meterRegistry) {
    batchSize =
        DistributionSummary.builder(BATCH_SIZE.getName())
            .description(BATCH_SIZE.getDescription())
            .serviceLevelObjectives(BATCH_SIZE.getDistributionSLOs())
            .register(meterRegistry);
    batchLengthBytes =
        DistributionSummary.builder(BATCH_LENGTH_BYTES.getName())
            .description(BATCH_LENGTH_BYTES.getDescription())
            .serviceLevelObjectives(BATCH_LENGTH_BYTES.getDistributionSLOs())
            .register(meterRegistry);
  }

  void observeBatchSize(final int size) {
    batchSize.record(size);
  }

  void observeBatchLengthBytes(final int lengthBytes) {
    final int batchLengthKiloBytes = Math.floorDiv(lengthBytes, 1024);
    batchLengthBytes.record(batchLengthKiloBytes);
  }

  @SuppressWarnings("NullableProblems")
  public enum SequencerMetricsDoc implements ExtendedMeterDocumentation {
    /** Histogram over the number of entries in each batch that is appended */
    BATCH_SIZE {
      private static final double[] BUCKETS = {1, 2, 3, 5, 10, 25, 50, 100, 500, 1000};

      @Override
      public String getDescription() {
        return "Histogram over the number of entries in each batch that is appended";
      }

      @Override
      public String getName() {
        return "zeebe.sequencer.batch.size";
      }

      @Override
      public Type getType() {
        return Type.DISTRIBUTION_SUMMARY;
      }

      @Override
      public double[] getDistributionSLOs() {
        return BUCKETS;
      }

      @Override
      public KeyName[] getAdditionalKeyNames() {
        return PartitionKeyNames.values();
      }
    },

    /** Histogram over the size, in Kilobytes, of the sequenced batches */
    BATCH_LENGTH_BYTES {
      private static final double[] BUCKETS = {0.256, 0.512, 1, 4, 8, 32, 128, 512, 1024, 4096};

      @Override
      public String getDescription() {
        return "Histogram over the size, in Kilobytes, of the sequenced batches";
      }

      @Override
      public String getName() {
        return "zeebe.sequencer.batch.length.bytes";
      }

      @Override
      public String getBaseUnit() {
        return "KiB";
      }

      @Override
      public Type getType() {
        return Type.DISTRIBUTION_SUMMARY;
      }

      @Override
      public double[] getDistributionSLOs() {
        return BUCKETS;
      }

      @Override
      public KeyName[] getAdditionalKeyNames() {
        return PartitionKeyNames.values();
      }
    }
  }
}
