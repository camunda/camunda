/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.hub.fanout;

import com.fasterxml.jackson.databind.JsonNode;

/** Handles the payload of one fan-out event type. The puller routes events by {@link #type()}. */
public interface FanoutEventHandler {

  String type();

  /** Throws to have the event retried; the cursor only advances once this returns. */
  void handle(String physicalTenantId, JsonNode data) throws Exception;
}
