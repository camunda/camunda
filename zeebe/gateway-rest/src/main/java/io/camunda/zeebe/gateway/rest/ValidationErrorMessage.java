/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.rest;

import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;

public class ValidationErrorMessage {

  final UserTaskIntent intent;
  final String message;

  public ValidationErrorMessage(final UserTaskIntent intent, final String message) {
    this.intent = intent;
    this.message = message;
  }

  public UserTaskIntent getIntent() {
    return intent;
  }

  public String getMessage() {
    return message;
  }
}
