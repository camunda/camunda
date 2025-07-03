/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.handler.sm;

import static io.camunda.migration.identity.MigrationUtil.normalizeGroupID;

import io.camunda.migration.api.MigrationException;
import io.camunda.migration.identity.client.ManagementIdentityClient;
import io.camunda.migration.identity.dto.Group;
import io.camunda.migration.identity.handler.MigrationHandler;
import io.camunda.service.GroupServices;
import io.camunda.service.GroupServices.GroupDTO;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class GroupMigrationHandler extends MigrationHandler<Group> {

  private final ManagementIdentityClient managementIdentityClient;
  private final GroupServices groupServices;

  private final AtomicInteger createdGroupCount = new AtomicInteger();
  private final AtomicInteger totalGroupCount = new AtomicInteger();

  public GroupMigrationHandler(
      final ManagementIdentityClient managementIdentityClient, final GroupServices groupServices) {
    this.managementIdentityClient = managementIdentityClient;
    this.groupServices = groupServices;
  }

  @Override
  protected List<Group> fetchBatch(final int page) {
    return managementIdentityClient.fetchGroups(page);
  }

  @Override
  protected void process(final List<Group> batch) {
    totalGroupCount.addAndGet(batch.size());

    batch.forEach(
        group -> {
          final var normalizedGroupId = normalizeGroupID(group);
          logger.debug(
              "Migrating Group: {} to a Group with the identifier: {}.", group, normalizedGroupId);
          try {
            final var groupDTO = new GroupDTO(normalizedGroupId, group.name(), "");
            groupServices.createGroup(groupDTO).join();
            createdGroupCount.incrementAndGet();
          } catch (final Exception e) {
            if (!isConflictError(e)) {
              throw new MigrationException("Failed to migrate group with ID: " + group.id(), e);
            }
            logger.debug("Group with ID '{}' already exists, skipping creation.", group.id());
          }
        });
  }

  @Override
  protected void logSummary() {
    logger.info(
        "Group migration completed: Created {} out of {} groups, the remaining existed already.",
        createdGroupCount.get(),
        totalGroupCount.get());
  }
}
