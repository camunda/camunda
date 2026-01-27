/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.appint.exporter.transport;

import io.camunda.appint.exporter.transport.Authentication.ApiKey;
import io.camunda.appint.exporter.transport.Authentication.None;

public sealed interface Authentication permits ApiKey, None {

  final class None implements Authentication {
    public static final None INSTANCE = new None();

    private None() {}
  }

  record ApiKey(String apiKey) implements Authentication {
    public static final String HEADER_NAME = "X-API-KEY";
  }
}
