/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.groupby.process.identity;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.IdentityType;
import io.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.AssigneeCandidateGroupService;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.LocalizationService;
import io.camunda.optimize.service.db.report.interpreter.distributedby.process.identity.ProcessDistributedByIdentityInterpreter;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class ProcessGroupByIdentityInterpreterHelper {
  private final DefinitionService definitionService;
  private final LocalizationService localizationService;
  private final AssigneeCandidateGroupService assigneeCandidateGroupService;

  public ProcessGroupByIdentityInterpreterHelper(
      final DefinitionService definitionService,
      final LocalizationService localizationService,
      final AssigneeCandidateGroupService assigneeCandidateGroupService) {
    this.definitionService = definitionService;
    this.localizationService = localizationService;
    this.assigneeCandidateGroupService = assigneeCandidateGroupService;
  }

  public Set<String> getUserTaskIds(final ProcessReportDataDto reportData) {
    return definitionService
        .extractUserTaskIdAndNames(
            reportData.getDefinitions().stream()
                .map(
                    definitionDto ->
                        definitionService.getDefinition(
                            DefinitionType.PROCESS,
                            definitionDto.getKey(),
                            definitionDto.getVersions(),
                            definitionDto.getTenantIds()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(ProcessDefinitionOptimizeDto.class::cast)
                .toList())
        .keySet();
  }

  public String resolveIdentityName(
      final String key, final Supplier<IdentityType> identityTypeSupplier) {
    return ProcessDistributedByIdentityInterpreter.DISTRIBUTE_BY_IDENTITY_MISSING_KEY.equals(key)
        ? localizationService.getDefaultLocaleMessageForMissingAssigneeLabel()
        : assigneeCandidateGroupService
            .getIdentityByIdAndType(key, identityTypeSupplier.get())
            .map(IdentityWithMetadataResponseDto::getName)
            .orElse(key);
  }
}
