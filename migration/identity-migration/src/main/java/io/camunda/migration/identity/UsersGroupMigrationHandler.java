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
import io.camunda.migration.identity.dto.UserGroups;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.migration.identity.midentity.ManagementIdentityTransformer;
import io.camunda.search.entities.MappingEntity;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingServices;
import io.camunda.service.MappingServices.MappingDTO;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class UsersGroupMigrationHandler extends MigrationHandler<UserGroups> {

  private static final String USERNAME_CLAIM = "sub";

  private final ManagementIdentityClient managementIdentityClient;
  private final ManagementIdentityTransformer managementIdentityTransformer;
  private final GroupServices groupServices;
  private final MappingServices mappingServices;

  public UsersGroupMigrationHandler(
      final ManagementIdentityClient managementIdentityClient,
      final ManagementIdentityTransformer managementIdentityTransformer,
      final GroupServices groupServices,
      final MappingServices mappingServices) {
    this.managementIdentityClient = managementIdentityClient;
    this.managementIdentityTransformer = managementIdentityTransformer;
    this.groupServices = groupServices;
    this.mappingServices = mappingServices;
  }

  @Override
  protected List<UserGroups> fetchBatch() {
    return managementIdentityClient.fetchUserGroups(SIZE);
  }

  @Override
  protected void process(final List<UserGroups> batch) {
    managementIdentityClient.updateMigrationStatus(batch.stream().map(this::processTask).toList());
  }

  public MigrationStatusUpdateRequest processTask(final UserGroups userGroups) {
    try {
      final var mapping =
          new MappingDTO(
              USERNAME_CLAIM,
              userGroups.username(),
              userGroups.username() + "_mapping",
              userGroups.username() + "_mapping");

      final long mappingKey =
          mappingServices
              .findMapping(mapping)
              .map(MappingEntity::mappingKey)
              .orElseGet(() -> mappingServices.createMapping(mapping).join().getMappingKey());
      for (final Group userGroup : userGroups.groups()) {
        final var groupKey = groupServices.getGroupByName(userGroup.name()).groupKey();
        assignMemberToGroup(groupKey, mappingKey);
      }
      return managementIdentityTransformer.toMigrationStatusUpdateRequest(userGroups, null);
    } catch (final Exception e) {
      return managementIdentityTransformer.toMigrationStatusUpdateRequest(userGroups, e);
    }
  }

  private void assignMemberToGroup(final long groupKey, final long mappingKey) {
    try {
      // TODO: revisit this while implementing the migration
      // groupServices.assignMember(groupKey, mappingKey, EntityType.MAPPING).join();
    } catch (final Exception e) {
      if (!isConflictError(e)) {
        throw e;
      }
    }
  }
}
