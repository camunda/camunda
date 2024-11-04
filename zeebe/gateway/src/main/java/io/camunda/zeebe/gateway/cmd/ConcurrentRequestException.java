/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.cmd;

import java.util.concurrent.TimeUnit;

public class ConcurrentRequestException extends ClientException {

  private static final String MESSAGE_FORMAT =
      "Expected to fetch tenants from Identity within %d%s, but there are too many concurrent requests; either increase the tenant request capacity, or scale Identity to complete requests faster.";

  public ConcurrentRequestException(final long timeout, final TimeUnit timeoutUnit) {
    super(String.format(MESSAGE_FORMAT, timeout, timeoutUnit));
  }
}
