/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metrics;

import io.micrometer.common.docs.KeyName;

/** Tag keys shared by the metrics that report the result of a request to the cluster. */
public enum ResultMetricKeyNames implements KeyName {

  /** The outcome of a request or of its answer */
  OUTCOME {
    @Override
    public String asString() {
      return "outcome";
    }
  },

  /** The error type of a failed answer, e.g. {@code grpc_resource_exhausted} or {@code http_503} */
  ERROR {
    @Override
    public String asString() {
      return "error";
    }
  }
}
