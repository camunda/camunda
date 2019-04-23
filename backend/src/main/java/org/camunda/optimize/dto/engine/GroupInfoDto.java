/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.engine;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Set;

@Setter
@Getter
public class GroupInfoDto {

  private List<GroupDto> groups;
  private Set<UserDto> groupUsers;

  public boolean containsGroup(String groupId) {
    GroupDto groupDto = new GroupDto();
    groupDto.setId(groupId);
    return groups.contains(groupDto);
  }
}
