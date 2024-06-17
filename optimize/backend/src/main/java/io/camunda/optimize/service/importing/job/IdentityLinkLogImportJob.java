/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.job;

import io.camunda.optimize.dto.optimize.IdentityDto;
import io.camunda.optimize.dto.optimize.IdentityType;
import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.importing.IdentityLinkLogEntryDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.usertask.IdentityLinkLogWriter;
import io.camunda.optimize.service.identity.PlatformUserTaskIdentityCache;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IdentityLinkLogImportJob extends DatabaseImportJob<IdentityLinkLogEntryDto> {

  private final IdentityLinkLogWriter identityLinkLogWriter;
  private final PlatformUserTaskIdentityCache platformUserTaskIdentityCache;
  private final ConfigurationService configurationService;

  public IdentityLinkLogImportJob(
      final IdentityLinkLogWriter identityLinkLogWriter,
      final PlatformUserTaskIdentityCache platformUserTaskIdentityCache,
      final ConfigurationService configurationService,
      final Runnable callback,
      final DatabaseClient databaseClient) {
    super(callback, databaseClient);
    this.identityLinkLogWriter = identityLinkLogWriter;
    this.platformUserTaskIdentityCache = platformUserTaskIdentityCache;
    this.configurationService = configurationService;
  }

  @Override
  protected void persistEntities(final List<IdentityLinkLogEntryDto> newOptimizeEntities) {
    final List<ImportRequestDto> importRequests =
        identityLinkLogWriter.generateIdentityLinkLogImports(newOptimizeEntities);
    databaseClient.executeImportRequestsAsBulk(
        "identity link logs",
        importRequests,
        configurationService.getSkipDataAfterNestedDocLimitReached());
    try {
      platformUserTaskIdentityCache.addIdentitiesIfNotPresent(
          mapToIdentityDtos(newOptimizeEntities));
    } catch (final Exception e) {
      log.warn(
          "Failed forwarding identities to assignee & candidate group service, "
              + "will be retried with future import batches.",
          e);
    }
  }

  private Set<IdentityDto> mapToIdentityDtos(
      final List<IdentityLinkLogEntryDto> newOptimizeEntities) {
    return newOptimizeEntities.stream()
        .map(
            entry -> {
              switch (entry.getType()) {
                case ASSIGNEE:
                  return Optional.ofNullable(entry.getUserId())
                      .map(id -> new IdentityDto(id, IdentityType.USER))
                      .orElse(null);
                case CANDIDATE:
                  return Optional.ofNullable(entry.getGroupId())
                      .map(id -> new IdentityDto(id, IdentityType.GROUP))
                      .orElse(null);
                default:
                case OWNER:
                  log.debug(
                      "Skipping non identity identityLinkLog entry of type: {}.", entry.getType());
                  return null;
              }
            })
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }
}
