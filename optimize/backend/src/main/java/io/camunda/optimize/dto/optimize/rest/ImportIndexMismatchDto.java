/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import java.io.Serializable;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@Builder
@EqualsAndHashCode
public class ImportIndexMismatchDto implements Serializable {

  private String indexName;
  private int sourceIndexVersion;
  private int targetIndexVersion;

  public ImportIndexMismatchDto(String indexName, int sourceIndexVersion, int targetIndexVersion) {
    this.indexName = indexName;
    this.sourceIndexVersion = sourceIndexVersion;
    this.targetIndexVersion = targetIndexVersion;
  }

  public ImportIndexMismatchDto() {}
}
