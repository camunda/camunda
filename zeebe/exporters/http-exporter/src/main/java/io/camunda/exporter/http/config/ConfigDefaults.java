/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.http.config;

public class ConfigDefaults {
  public static final Integer DEFAULT_BATCH_SIZE = 100;
  public static final Long DEFAULT_BATCH_INTERVAL = 5000L; // in milliseconds
  public static final Integer DEFAULT_MAX_RETRIES = 3;
  public static final Long DEFAULT_RETRY_DELAY = 1000L; // in milliseconds
  public static final Long DEFAULT_TIMEOUT = 30000L; // in milliseconds
  public static final Boolean DEFAULT_CONTINUE_ON_ERROR =
      false; // Default behavior for error handling
}
