/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.rest.dto.activity;

import java.util.ArrayList;
import java.util.List;

public class ActivityInstanceTreeDto {

  private List<ActivityInstanceDto> children = new ArrayList<>();

  public List<ActivityInstanceDto> getChildren() {
    return children;
  }

  public void setChildren(List<ActivityInstanceDto> children) {
    this.children = children;
  }

}
