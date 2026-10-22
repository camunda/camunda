/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector.swim;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter;

@SuppressWarnings("NullableProblems")
enum SwimDetectorMetricsDoc implements ExtendedMeterDocumentation {
  /** The consolidated liveness of a broker, as seen by this broker. */
  BROKER_STATE {
    @Override
    public String getName() {
      return "zeebe.system.partition.swim.detector.broker.state";
    }

    @Override
    public Meter.Type getType() {
      return Meter.Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "The liveness of a broker consolidated from the SWIM views the system partition brokers"
          + " reported: 0 unknown, 1 healthy, 2 suspect, 3 dead";
    }

    @Override
    public KeyName[] getKeyNames() {
      return BrokerKeyNames.values();
    }
  },

  /** For how long no observer has seen a broker. */
  BROKER_GONE_DURATION {
    @Override
    public String getName() {
      return "zeebe.system.partition.swim.detector.broker.gone.duration";
    }

    @Override
    public Meter.Type getType() {
      return Meter.Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "For how many seconds every observer confirming it has not seen a broker; 0 while any"
          + " observer sees it";
    }

    @Override
    public KeyName[] getKeyNames() {
      return BrokerKeyNames.values();
    }
  },

  /** The liveness of a region, aggregated from its system partition brokers. */
  REGION_STATE {
    @Override
    public String getName() {
      return "zeebe.system.partition.swim.detector.region.state";
    }

    @Override
    public Meter.Type getType() {
      return Meter.Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "The liveness of a region aggregated from its system partition brokers: 0 unknown,"
          + " 1 active, 2 suspect, 3 failed";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {RegionKeyNames.REGION};
    }
  },

  /** How many brokers' reports this broker currently counts. */
  FRESH_REPORTS {
    @Override
    public String getName() {
      return "zeebe.system.partition.swim.detector.reports.fresh";
    }

    @Override
    public Meter.Type getType() {
      return Meter.Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "How many system partition brokers reported their SWIM view recently enough to count,"
          + " this broker included";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[0];
    }
  },

  /** When the system partition committed a failover from a failed region to a target region. */
  REGION_FAILOVER {
    @Override
    public String getName() {
      return "zeebe.system.partition.swim.detector.region.failover";
    }

    @Override
    public Meter.Type getType() {
      return Meter.Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "The epoch seconds at which the system partition leader decided a failover from the"
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
