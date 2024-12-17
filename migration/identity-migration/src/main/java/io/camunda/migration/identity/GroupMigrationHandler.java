/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import io.camunda.migration.identity.dto.Group;
import io.camunda.migration.identity.dto.MigrationStatusUpdateRequest;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.migration.identity.midentity.ManagementIdentityTransformer;
import io.camunda.migration.identity.service.GroupService;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GroupMigrationHandler implements MigrationHandler {

  private final ManagementIdentityClient managementIdentityClient;
  private final ManagementIdentityTransformer managementIdentityTransformer;
  private final GroupService groupService;

  public GroupMigrationHandler(
      final ManagementIdentityClient managementIdentityClient,
      final ManagementIdentityTransformer managementIdentityTransformer,
      final GroupService groupService) {
    this.managementIdentityClient = managementIdentityClient;
    this.managementIdentityTransformer = managementIdentityTransformer;
    this.groupService = groupService;
  }

  @Override
  public void migrate() {
    List<Group> groups;
    do {
      groups = managementIdentityClient.fetchGroups(SIZE);
      managementIdentityClient.updateMigrationStatus(
          groups.stream().map(this::createGroup).toList());
    } while (!groups.isEmpty());
  }

  private MigrationStatusUpdateRequest createGroup(final Group group) {
    try {
      groupService.create(group.name());
    } catch (final Exception e) {
      if (!isConflictError(e)) {
        return managementIdentityTransformer.toMigrationStatusUpdateRequest(group, e);
      }
    }
    return managementIdentityTransformer.toMigrationStatusUpdateRequest(group, null);
  }
}
