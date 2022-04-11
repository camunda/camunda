/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.job.importing;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.importing.IdentityLinkLogEntryDto;
import org.camunda.optimize.service.AssigneeCandidateGroupService;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil;
import org.camunda.optimize.service.es.writer.usertask.IdentityLinkLogWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class IdentityLinkLogImportJob extends ElasticsearchImportJob<IdentityLinkLogEntryDto> {

  private final IdentityLinkLogWriter identityLinkLogWriter;
  private final AssigneeCandidateGroupService assigneeCandidateGroupService;
  private final ConfigurationService configurationService;

  public IdentityLinkLogImportJob(final IdentityLinkLogWriter identityLinkLogWriter,
                                  final AssigneeCandidateGroupService assigneeCandidateGroupService,
                                  final ConfigurationService configurationService,
                                  final Runnable callback) {
    super(callback);
    this.identityLinkLogWriter = identityLinkLogWriter;
    this.assigneeCandidateGroupService = assigneeCandidateGroupService;
    this.configurationService = configurationService;
  }

  @Override
  protected void persistEntities(final List<IdentityLinkLogEntryDto> newOptimizeEntities) {
    final List<ImportRequestDto> importRequests = identityLinkLogWriter.generateIdentityLinkLogImports(
      newOptimizeEntities);
    ElasticsearchWriterUtil.executeImportRequestsAsBulk(
      "identity link logs",
      importRequests,
      configurationService.getSkipDataAfterNestedDocLimitReached()
    );
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
            return Optional.ofNullable(entry.getUserId())
              .map(id -> new IdentityDto(id, IdentityType.USER))
              .orElse(null);
          case CANDIDATE:
            return Optional.ofNullable(entry.getGroupId())
              .map(id -> new IdentityDto(id, IdentityType.GROUP))
              .orElse(null);
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
