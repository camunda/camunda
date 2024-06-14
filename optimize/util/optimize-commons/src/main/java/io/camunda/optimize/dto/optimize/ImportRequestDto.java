/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.dto.optimize;

import io.camunda.optimize.service.db.schema.ScriptData;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@FieldNameConstants(asEnum = true)
public class ImportRequestDto {

  private String importName;
  private String indexName;
  private ScriptData scriptData;
  private String id;
  private Object source;
  private RequestType type;
  private int retryNumberOnConflict;
}
