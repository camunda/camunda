/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class HistoricDecisionInputInstanceDto implements EngineDto {
  private String id;
  private String type;
  private Object value;
  private String decisionInstanceId;
  private String clauseId;
  private String clauseName;
  private String errorMessage;
  private Date createTime;
  private Date removalTime;
  private String rootProcessInstanceId;

  public HistoricDecisionInputInstanceDto() {
  }
}
