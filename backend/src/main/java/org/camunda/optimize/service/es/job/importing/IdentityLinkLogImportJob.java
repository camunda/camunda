/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.job.importing;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.importing.IdentityLinkLogEntryDto;
import org.camunda.optimize.service.AssigneeCandidateGroupService;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.usertask.IdentityLinkLogWriter;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class IdentityLinkLogImportJob extends ElasticsearchImportJob<IdentityLinkLogEntryDto> {
  private final IdentityLinkLogWriter identityLinkLogWriter;
  private final AssigneeCandidateGroupService assigneeCandidateGroupService;

  public IdentityLinkLogImportJob(final IdentityLinkLogWriter identityLinkLogWriter,
                                  final AssigneeCandidateGroupService assigneeCandidateGroupService,
                                  final Runnable callback) {
    super(callback);
    this.identityLinkLogWriter = identityLinkLogWriter;
    this.assigneeCandidateGroupService = assigneeCandidateGroupService;
  }

  @Override
  protected void persistEntities(final List<IdentityLinkLogEntryDto> newOptimizeEntities) {
    identityLinkLogWriter.importIdentityLinkLogs(newOptimizeEntities);
    try {
      assigneeCandidateGroupService.addIdentitiesIfNotPresent(mapToIdentityDtos(newOptimizeEntities));
    } catch (final Exception e) {
      log.warn(
        "Failed forwarding identities to assignee & candidate group service, " +
          "will be retried with future import batches.", e
      );
    }
  }

  private Set<IdentityDto> mapToIdentityDtos(final List<IdentityLinkLogEntryDto> newOptimizeEntities) {
    return newOptimizeEntities.stream()
      .map(entry -> {
        switch (entry.getType()) {
          case ASSIGNEE:
            return new IdentityDto(entry.getUserId(), IdentityType.USER);
          case CANDIDATE:
            return new IdentityDto(entry.getGroupId(), IdentityType.GROUP);
          default:
          case OWNER:
            log.debug("Skipping non identity identityLinkLog entry of type: {}.", entry.getType());
            return null;
        }
      })
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());
  }
}
