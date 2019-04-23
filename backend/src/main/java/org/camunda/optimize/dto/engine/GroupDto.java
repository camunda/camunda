/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.engine;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GroupDto {

  protected String id;
  protected String name;
  protected String type;

  @Override
  public boolean equals(Object object) {
    if( object instanceof GroupDto) {
      GroupDto anotherGroupDto = (GroupDto) object;
      return this.id.equals(anotherGroupDto.id);
    }
    return false;
  }
}
