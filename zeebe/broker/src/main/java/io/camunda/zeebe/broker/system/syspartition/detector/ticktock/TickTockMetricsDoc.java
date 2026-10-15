/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector.ticktock;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter;

@SuppressWarnings("NullableProblems")
enum TickTockMetricsDoc implements ExtendedMeterDocumentation {
  /** How many ticks committed since a broker was last seen. */
  MISSED_TICKS {
    @Override
    public String getName() {
      return "zeebe.system.partition.failure.detector.missed.ticks";
    }

    @Override
    public Meter.Type getType() {
      return Meter.Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "How many ticks committed to the system partition since a broker was last seen";
    }

    @Override
    public KeyName[] getKeyNames() {
      return BrokerKeyNames.values();
    }
  },

  /** The liveness state of a broker, as seen by this broker. */
  BROKER_STATE {
    @Override
    public String getName() {
      return "zeebe.system.partition.failure.detector.broker.state";
    }

    @Override
    public Meter.Type getType() {
      return Meter.Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "The liveness state of a broker as seen by the system partition's failure detector:"
          + " 0 unknown, 1 healthy, 2 suspect, 3 dead";
    }

    @Override
    public KeyName[] getKeyNames() {
      return BrokerKeyNames.values();
    }
  },

  /** The id of the latest tick applied by this broker. */
  TICK {
    @Override
    public String getName() {
      return "zeebe.system.partition.failure.detector.tick";
    }

    @Override
    public Meter.Type getType() {
      return Meter.Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "The id of the latest tick of the system partition's failure detector applied by this"
          + " broker";
    }
  },

  /** How many tocks the leader received, by sending broker. */
  TOCKS_RECEIVED {
    @Override
    public String getName() {
      return "zeebe.system.partition.failure.detector.tocks.received";
    }

    @Override
    public Meter.Type getType() {
      return Meter.Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "How many tocks the system partition leader received, by sending broker";
    }

    @Override
    public KeyName[] getKeyNames() {
      return BrokerKeyNames.values();
    }
  },

  /** How long a tick put by the leader takes to commit. */
  TICK_COMMIT_LATENCY {
    @Override
    public String getName() {
      return "zeebe.system.partition.failure.detector.tick.commit.latency";
    }

    @Override
    public Meter.Type getType() {
      return Meter.Type.TIMER;
    }

    @Override
    public String getDescription() {
      return "How long a tick put by the system partition leader takes to commit";
    }
  },

  REGION_STATE {
    @Override
    public String getName() {
      return "zeebe.system.partition.failure.detector.region.state";
    }

    @Override
    public Meter.Type getType() {
      return Meter.Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "The state of a region as seen by the system partition's failure detector:"
          + " 0 unknown, 1 active, 2 suspect, 3 failed";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {RegionKeyNames.REGION};
    }
  },

  REGION_FAILOVER_TICK {
    @Override
    public String getName() {
      return "zeebe.system.partition.failure.detector.region.failover.tick";
    }

    @Override
    public Meter.Type getType() {
      return Meter.Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "The id of the tick on which the system partition committed a failover from the"
          + " failed region to the target region";
    }

    @Override
    public KeyName[] getKeyNames() {
      return RegionKeyNames.values();
    }
  };

  enum BrokerKeyNames implements KeyName {
    /** The member id of the observed broker. */
    BROKER {
      @Override
      public String asString() {
        return "broker";
      }
    }
  }

  enum RegionKeyNames implements KeyName {
    /** The region a meter is about; for a failover, the failed region. */
    REGION {
      @Override
      public String asString() {
        return "region";
      }
    },

    /** The region a failover moves to. */
    TARGET_REGION {
      @Override
      public String asString() {
        return "target_region";
      }
    }
  }
}
