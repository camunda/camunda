/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metrics;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;

/** Metrics shared across all app types (Starter and Worker). */
public enum AppMetricsDoc implements ExtendedMeterDocumentation {
  /**
   * A gauge set to 1 when the client successfully connects to the gateway (i.e. after the first
   * successful topology request), and 0 otherwise. This metric is used by the verification workflow
   * to confirm that the client is connected, regardless of whether the client uses gRPC or REST.
   */
  CONNECTED {
    @Override
    public String getDescription() {
      return "Set to 1 when the client successfully connects to the gateway (topology received), 0 otherwise.";
    }

    @Override
    public String getName() {
      return "app.connected";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }
  },

  /**
   * Counts completed client requests by outcome, as the client observed them. Unlike the
   * gateway-side request metrics, this includes failures that never reach a broker (timeouts,
   * connection errors), and works whether the client uses gRPC or REST.
   */
  REQUESTS {
    private static final KeyName[] KEY_NAMES = RequestKeyNames.values();

    @Override
    public String getDescription() {
      return "Number of completed client requests, by command and outcome.";
    }

    @Override
    public String getName() {
      return "app.requests";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public KeyName[] getKeyNames() {
      return KEY_NAMES;
    }
  };

  public enum RequestKeyNames implements KeyName {
    /** The command that was sent, e.g. {@code create_instance} */
    COMMAND {
      @Override
      public String asString() {
        return "command";
      }
    },

    /**
     * {@code ok} on success; otherwise the HTTP status code, the gRPC status code, or the exception
     * type when the request failed without a response
     */
    STATUS {
      @Override
      public String asString() {
        return "status";
      }
    },

    /** The title of the REST problem detail, if any, which tells apart e.g. kinds of 503 */
    REASON {
      @Override
      public String asString() {
        return "reason";
      }
    }
  }
}
