/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.variable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Accessors(chain = true)
public class ProcessVariableDto implements OptimizeDto {

  private String id;
  private String name;
  private String type;
  private List<String> value;
  private OffsetDateTime timestamp;
  private Map<String, Object> valueInfo;
  private String processDefinitionKey;
  private String processDefinitionId;
  private String processInstanceId;
  private Long version;
  private String engineAlias;
  private String tenantId;
}
