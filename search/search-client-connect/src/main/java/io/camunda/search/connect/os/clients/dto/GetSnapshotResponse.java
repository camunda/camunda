/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.os.clients.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GetSnapshotResponse(List<SnapshotInformation> snapshots) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  record SnapshotInformation(
      String snapshot,
      String uuid,
      SnapshotState state,
      List<Object> failures,
      @JsonProperty("start_time_in_millis") Long startTimeInMillis,
      Map<String, Object> metadata) {}

  enum SnapshotState {
    FAILED("FAILED"),
    PARTIAL("PARTIAL"),
    STARTED("STARTED"),
    SUCCESS("SUCCESS");
    private final String state;

    SnapshotState(final String state) {
      this.state = state;
    }

    @Override
    public String toString() {
      return state;
    }
  }
}
