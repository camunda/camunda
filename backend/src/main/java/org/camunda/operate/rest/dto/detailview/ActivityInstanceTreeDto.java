/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.rest.dto.detailview;

import java.util.ArrayList;
import java.util.List;

public class ActivityInstanceTreeDto {

  private List<DetailViewActivityInstanceDto> children = new ArrayList<>();

  public List<DetailViewActivityInstanceDto> getChildren() {
    return children;
  }

  public void setChildren(List<DetailViewActivityInstanceDto> children) {
    this.children = children;
  }

}
