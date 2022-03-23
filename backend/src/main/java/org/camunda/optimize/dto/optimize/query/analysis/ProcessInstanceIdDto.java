/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.analysis;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_INSTANCE_ID;

public class ProcessInstanceIdDto extends IdResponseDto {
  @Override
  @JsonProperty(PROCESS_INSTANCE_ID)
  public void setId(final String id) {
    super.setId(id);
  }
}
