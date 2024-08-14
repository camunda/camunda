/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize;

import io.camunda.optimize.service.db.schema.ScriptData;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ImportRequestDto {

  private String importName;
  private String indexName;
  private ScriptData scriptData;
  private String id;
  private Object source;
  private RequestType type;
  private int retryNumberOnConflict;

  public enum Fields {
    importName,
    indexName,
    scriptData,
    id,
    source,
    type,
    retryNumberOnConflict
  }
}
