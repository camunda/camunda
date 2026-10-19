/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.hub.fanout;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/** Consumer copy of Hub's fan-out envelope (CloudEvents 1.0 JSON format). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CloudEventEnvelope(
    String specversion,
    String id,
    String source,
    String type,
    String time,
    String datacontenttype,
    long seq,
    JsonNode data,
    String dataref,
    Long datasize,
    String datasha256) {

  public static final String SPEC_VERSION = "1.0";
  public static final String HEADER_NEXT_CURSOR = "Fanout-Next-Cursor";
  public static final String HEADER_HAS_MORE = "Fanout-Has-More";
}
