/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.exceptions;

public class ForbiddenException extends APIException {

  public static final String TYPE = "Forbidden resource access";

  public ForbiddenException(final String message) {
    super(message);
  }
}
