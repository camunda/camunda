/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;

@EqualsAndHashCode
@ToString
@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
@FieldNameConstants
public class EventCountDto {

  private String group;
  private String source;
  private String eventName;
  private Long count;

}
