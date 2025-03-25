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
import io.camunda.security.auth.Authentication;
import io.camunda.service.GroupServices;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GroupMigrationHandler extends MigrationHandler<Group> {

  private final ManagementIdentityClient managementIdentityClient;
  private final ManagementIdentityTransformer managementIdentityTransformer;
  private final GroupServices groupServices;

  public GroupMigrationHandler(
      final Authentication authentication,
      final ManagementIdentityClient managementIdentityClient,
      final ManagementIdentityTransformer managementIdentityTransformer,
      final GroupServices groupServices) {
    this.managementIdentityClient = managementIdentityClient;
    this.managementIdentityTransformer = managementIdentityTransformer;
    this.groupServices = groupServices.withAuthentication(authentication);
  }

  @Override
  protected List<Group> fetchBatch() {
    return managementIdentityClient.fetchGroups(SIZE);
  }

  @Override
  protected void process(final List<Group> batch) {
    managementIdentityClient.updateMigrationStatus(batch.stream().map(this::processTask).toList());
  }

  private MigrationStatusUpdateRequest processTask(final Group group) {
    try {
      // TODO: Revisit this part
//      groupServices.createGroup(group.name()).join();
    } catch (final Exception e) {
      if (!isConflictError(e)) {
        return managementIdentityTransformer.toMigrationStatusUpdateRequest(group, e);
      }
    }
    return managementIdentityTransformer.toMigrationStatusUpdateRequest(group, null);
  }
}
