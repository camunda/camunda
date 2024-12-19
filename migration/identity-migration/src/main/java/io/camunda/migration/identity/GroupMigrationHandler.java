/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import static io.camunda.migration.identity.midentity.ManagementIdentityTransformer.toMigrationStatusUpdateRequest;

import io.camunda.migration.identity.dto.Group;
import io.camunda.migration.identity.dto.MigrationStatusUpdateRequest;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.security.auth.Authentication;
import io.camunda.service.GroupServices;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GroupMigrationHandler implements MigrationHandler {

  private final ManagementIdentityClient managementIdentityClient;
  private final GroupServices groupServices;

  public GroupMigrationHandler(
      final Authentication authentication,
      final ManagementIdentityClient managementIdentityClient,
      final GroupServices groupServices) {
    this.managementIdentityClient = managementIdentityClient;
    this.groupServices = groupServices.withAuthentication(authentication);
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
      groupServices.createGroup(group.name()).join();
    } catch (final Exception e) {
      if (!isConflictError(e)) {
        return toMigrationStatusUpdateRequest(group, e);
      }
    }
    return toMigrationStatusUpdateRequest(group, null);
  }
}
