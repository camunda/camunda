/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.backpressure;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;

@SuppressWarnings("NullableProblems")
public enum BackpressureMetricsDoc implements ExtendedMeterDocumentation {
  /** Number of requests dropped due to backpressure */
  DROPPED_REQUEST_COUNT {
    @Override
    public String getDescription() {
      return "Number of requests dropped due to backpressure";
    }

    @Override
    public String getName() {
      return "zeebe.dropped.request.count.total";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /** Number of requests received */
  TOTAL_REQUEST_COUNT {
    @Override
    public String getDescription() {
      return "Number of requests received";
    }

    @Override
    public String getName() {
      return "zeebe.received.request.count.total";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /** Current number of request inflight */
  CURRENT_INFLIGHT {
    @Override
    public String getDescription() {
      return "Current number of request inflight";
    }

    @Override
    public String getName() {
      return "zeebe.backpressure.inflight.requests.count";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /** Current limit for number of inflight requests */
  CURRENT_LIMIT {
    @Override
    public String getDescription() {
      return "Current limit for number of inflight requests";
    }

    @Override
    public String getName() {
      return "zeebe.backpressure.requests.limit";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  }
}
