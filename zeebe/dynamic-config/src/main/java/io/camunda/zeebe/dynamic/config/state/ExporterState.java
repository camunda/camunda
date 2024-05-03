/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

/**
 * Represents the state of an exporter. The full configuration of this exporter must be provided as
 * part of the application configuration. Here we only keep track of whether it is enabled or
 * disabled. Sensitive information like access details must not be added here.
 */
public record ExporterState(State state) {
  public enum State {
    ENABLED,
    DISABLED
  }
}
