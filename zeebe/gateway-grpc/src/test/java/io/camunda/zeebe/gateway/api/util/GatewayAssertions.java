/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.api.util;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.assertj.core.api.Condition;

public final class GatewayAssertions {

  public static <T extends Throwable> Condition<T> statusRuntimeExceptionWithStatusCode(
      final Status.Code expected) {
    return new Condition<>(
        throwable -> {
          if (!(throwable instanceof final StatusRuntimeException statusRuntimeException)) {
            return false;
          }
          final Status actualStatus = statusRuntimeException.getStatus();
          return actualStatus.getCode().equals(expected);
        },
        "StatusRuntimeException with status code '%s'",
        expected);
  }
}
