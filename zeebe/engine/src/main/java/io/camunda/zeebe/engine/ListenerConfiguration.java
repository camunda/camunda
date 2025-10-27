/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine;

import java.util.List;

public record ListenerConfiguration(
    List<String> eventTypes, String type, String retries, boolean afterLocal) {
  public static final String ALL_EVENT_TYPES = "all";
}
