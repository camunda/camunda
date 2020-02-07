/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.persistence;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;

@Getter
@ToString
@FieldNameConstants
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class BusinessKeyDto {

  private String processInstanceId;
  private String businessKey;

}
