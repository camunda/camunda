/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter;
import java.time.Duration;

@SuppressWarnings("NullableProblems")
public enum PhysicalTenantDiskUsageMetricsDoc implements ExtendedMeterDocumentation {
  /** Disk space used by the data of a physical tenant on this broker */
  USED_BYTES {
    @Override
    public String getName() {
      return "zeebe.broker.disk.used.bytes";
    }

    @Override
    public Meter.Type getType() {
      return Meter.Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "Disk space used by the data of a physical tenant on this broker, in bytes";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {PartitionKeyNames.PHYSICAL_TENANT};
    }
  },

  /** Disk usage limit of a physical tenant on this broker, per threshold */
  LIMIT_BYTES {
    @Override
    public String getName() {
      return "zeebe.broker.disk.used.limit.bytes";
    }

    @Override
    public Meter.Type getType() {
      return Meter.Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "Disk usage limit of a physical tenant on this broker, in bytes, above which its"
          + " processing (threshold=processing) or replication (threshold=replication) pauses";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {PartitionKeyNames.PHYSICAL_TENANT, UsedSpaceKeyNames.THRESHOLD};
    }
  },

  /** Time taken to measure the disk space used by a physical tenant */
  SCAN_DURATION {
    private static final Duration[] BUCKETS = {
      Duration.ofMillis(1),
      Duration.ofMillis(5),
      Duration.ofMillis(10),
      Duration.ofMillis(25),
      Duration.ofMillis(50),
      Duration.ofMillis(100),
      Duration.ofMillis(250),
      Duration.ofMillis(500),
      Duration.ofSeconds(1)
    };

    @Override
    public String getName() {
      return "zeebe.broker.disk.used.scan.duration";
    }

    @Override
    public Meter.Type getType() {
      return Meter.Type.TIMER;
    }

    @Override
    public String getDescription() {
      return "Time taken to walk the data directories of a physical tenant to measure its disk"
          + " usage";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {PartitionKeyNames.PHYSICAL_TENANT};
    }

    @Override
    public Duration[] getTimerSLOs() {
      return BUCKETS;
    }
  };

  public enum UsedSpaceKeyNames implements KeyName {
    THRESHOLD {
      @Override
      public String asString() {
        return "threshold";
      }
    }
  }
}
