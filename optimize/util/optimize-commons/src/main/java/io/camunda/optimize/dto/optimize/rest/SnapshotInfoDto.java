/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.elasticsearch.snapshots.SnapshotState;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class SnapshotInfoDto {

  private String snapshotName;
  private SnapshotState state;
  private OffsetDateTime startTime;
  private List<String> failures;
}
