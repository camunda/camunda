/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate31To32.dto;

import lombok.Data;

@Data
public class AlertCreation31Dto {

  protected String name;
  protected String reportId;
  protected Double threshold;
  protected boolean fixNotification;
  protected String email;
  protected String webhook;
}