/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query;

import java.util.ArrayList;
import java.util.List;

/** POC: persisted role with the mapping rules, users, groups and clients that hold it. */
public class AccessRoleDto {

  private String id;
  private String name;
  private String description;
  private List<String> mappingRuleIds = new ArrayList<>();
  private List<String> userIds = new ArrayList<>();
  private List<String> groupIds = new ArrayList<>();
  private List<String> clientIds = new ArrayList<>();

  public AccessRoleDto() {}

  public AccessRoleDto(final String id, final String name) {
    this.id = id;
    this.name = name;
  }

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(final String description) {
    this.description = description;
  }

  public List<String> getMappingRuleIds() {
    return mappingRuleIds;
  }

  public void setMappingRuleIds(final List<String> mappingRuleIds) {
    this.mappingRuleIds = mappingRuleIds;
  }

  public List<String> getUserIds() {
    return userIds;
  }

  public void setUserIds(final List<String> userIds) {
    this.userIds = userIds;
  }

  public List<String> getGroupIds() {
    return groupIds;
  }

  public void setGroupIds(final List<String> groupIds) {
    this.groupIds = groupIds;
  }

  public List<String> getClientIds() {
    return clientIds;
  }

  public void setClientIds(final List<String> clientIds) {
    this.clientIds = clientIds;
  }
}
