/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.engine;

import lombok.Data;

import java.util.List;

@Data
public class AuthorizationDto {
  protected String id;
  protected Integer type;
  protected List<String> permissions;
  protected String userId;
  protected String groupId;
  protected Integer resourceType;
  protected String resourceId;
}
