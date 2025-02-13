/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.stream;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;

@SuppressWarnings("NullableProblems")
public enum JobClientStreamMetricsDoc implements ExtendedMeterDocumentation {
  /** The count of known job stream servers/brokers */
  SERVERS {
    @Override
    public String getDescription() {
      return "The count of known job stream servers/brokers";
    }

    @Override
    public String getName() {
      return "zeebe.gateway.job.stream.servers";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }
  },

  /** The count of known job stream clients */
  CLIENTS {
    @Override
    public String getDescription() {
      return "The count of known job stream clients";
    }

    @Override
    public String getName() {
      return "zeebe.gateway.job.stream.clients";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }
  },

  /** Total count of aggregated streams */
  AGGREGATED_STREAMS {

    @Override
    public String getDescription() {
      return "Total count of aggregated streams";
    }

    @Override
    public String getName() {
      return "zeebe.gateway.job.stream.streams";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }
  },

  /** Distribution of client count per aggregated stream */
  AGGREGATED_CLIENTS {
    /* Default buckets from Prometheus' histogram */
    private static final double[] BUCKETS = {
      .005, .01, .025, .05, .075, .1, .25, .5, .75, 1, 2.5, 5, 7.5, 10
    };

    @Override
    public String getDescription() {
      return "Distribution of client count per aggregated stream";
    }

    @Override
    public String getName() {
      return "zeebe.gateway.job.stream.aggregated.stream.clients";
    }

    @Override
    public Type getType() {
      return Type.DISTRIBUTION_SUMMARY;
    }

    @Override
    public double[] getDistributionSLOs() {
      return BUCKETS;
    }
  },

  /** Count of pushed payloads, tagged by result status (success, failure) */
  PUSHES {
    @Override
    public String getDescription() {
      return "Count of pushed payloads, tagged by result status (success, failure)";
    }

    @Override
    public String getName() {
      return "zeebe.gateway.job.stream.push";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {PushKeyNames.STATUS};
    }
  },

  /** Total number of failed attempts when pushing jobs to the clients, grouped by code */
  PUSH_TRY_FAILED_COUNT {
    @Override
    public String getDescription() {
      return "Total number of failed attempts when pushing jobs to the clients, grouped by code";
    }

    @Override
    public String getName() {
      return "zeebe.gateway.job.stream.";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {PushKeyNames.CODE};
    }
  };

  public enum PushKeyNames implements KeyName {
    /** The status code of a push attempt; one of {@link PushResultTag} */
    STATUS {
      @Override
      public String asString() {
        return "status";
      }
    },

    /** The result code on a failed push attempt */
    CODE {
      @Override
      public String asString() {
        return "code";
      }
    }
  }

  public enum PushResultTag {
    SUCCESS("success"),
    FAILURE("failure");

    private final String tagValue;

    PushResultTag(final String tagValue) {
      this.tagValue = tagValue;
    }

    public String getTagValue() {
      return tagValue;
    }
  }
}
