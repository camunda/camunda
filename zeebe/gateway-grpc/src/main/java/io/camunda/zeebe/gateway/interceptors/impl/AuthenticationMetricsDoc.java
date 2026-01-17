/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.interceptors.impl;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;

public enum AuthenticationMetricsDoc implements ExtendedMeterDocumentation {
  /**
   * Captures how long it takes to authenticate a gRPC request. Can be filtered by authentication
   * method (e.g., OIDC, basic).
   */
  LATENCY {
    @Override
    public String getName() {
      return "zeebe.gateway.grpc.auth.latency";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getDescription() {
      return "Captures how long it takes to authenticate a gRPC request";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {LatencyKeyNames.AUTH_METHOD};
    }
  };

  public enum LatencyKeyNames implements KeyName {
    AUTH_METHOD {
      @Override
      public String asString() {
        return "method";
      }
    },
    /** Indicates if authentication was successful or not; possible values are */
    AUTH_RESULT {
      @Override
      public String asString() {
        return "result";
      }
    }
  }

  public enum AuthResultValues {
    SUCCESS("success"),
    FAILURE("failure");

    private final String value;

    AuthResultValues(final String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }
}
