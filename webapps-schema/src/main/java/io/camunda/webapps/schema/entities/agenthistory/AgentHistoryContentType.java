/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.agenthistory;

/**
 * Entity-layer enum for the content type of a history entry content block. {@code UNKNOWN} is used
 * for any protocol value that has no explicit mapping (e.g. {@code UNSPECIFIED} or future types);
 * the content is still stored with all available fields so data is not silently discarded.
 */
public enum AgentHistoryContentType {
  UNKNOWN,
  TEXT,
  DOCUMENT,
  OBJECT
}
