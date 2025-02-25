/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.metrics;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;

/** Number of requests currently queued due to long polling */
@SuppressWarnings("NullableProblems")
public enum LongPollingMetricsDoc implements ExtendedMeterDocumentation {
  REQUESTS_QUEUED_CURRENT {
    @Override
    public String getDescription() {
      return "Number of requests currently queued due to long polling";
    }

    @Override
    public String getName() {
      return "zeebe.long.polling.queued.current";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public KeyName[] getKeyNames() {
      return RequestsQueuedKeyNames.values();
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return GatewayKeyNames.values();
    }
  };

  @SuppressWarnings("NullableProblems")
  public enum RequestsQueuedKeyNames implements KeyName {
    /** The job type associated with the blocked request */
    TYPE {
      @Override
      public String asString() {
        return "type";
      }
    }
  }

  @SuppressWarnings("NullableProblems")
  public enum GatewayKeyNames implements KeyName {
    /** Distinguishes between the gateway protocol */
    GATEWAY_PROTOCOL {
      @Override
      public String asString() {
        return "protocol";
      }
    }
  }

  /** The possible values for the gateway type */
  public enum GatewayProtocol {
    REST("rest"),
    GRPC("grpc");

    private final String value;

    GatewayProtocol(final String value) {
      this.value = value;
    }

    public String value() {
      return value;
    }
  }
}
