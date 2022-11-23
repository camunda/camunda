/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.log;

import io.camunda.zeebe.util.exception.RecoverableException;

public final class DispatcherClaimException extends RecoverableException {

  public DispatcherClaimException(final String message) {
    super(message);
  }
}
