/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.distributedby.process.identity;

import static io.camunda.optimize.service.db.report.interpreter.distributedby.process.identity.ProcessDistributedByIdentityInterpreter.DISTRIBUTE_BY_IDENTITY_MISSING_KEY;
import static io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult.createDistributedByResult;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.IdentityType;
import io.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.AssigneeCandidateGroupService;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.LocalizationService;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewResult;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ProcessDistributedByIdentityInterpreterHelper {
  private final AssigneeCandidateGroupService assigneeCandidateGroupService;
  private final DefinitionService definitionService;
  private final LocalizationService localizationService;

  public ProcessDistributedByIdentityInterpreterHelper(
      final AssigneeCandidateGroupService assigneeCandidateGroupService,
      final DefinitionService definitionService,
      final LocalizationService localizationService) {
    this.assigneeCandidateGroupService = assigneeCandidateGroupService;
    this.definitionService = definitionService;
    this.localizationService = localizationService;
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
                .collect(Collectors.toList()))
        .keySet();
  }

  public String resolveIdentityName(
      final String key, final Supplier<IdentityType> identityTypeSupplier) {
    if (DISTRIBUTE_BY_IDENTITY_MISSING_KEY.equals(key)) {
      return localizationService.getDefaultLocaleMessageForMissingAssigneeLabel();
    }
    return assigneeCandidateGroupService
        .getIdentityByIdAndType(key, identityTypeSupplier.get())
        .map(IdentityWithMetadataResponseDto::getName)
        .orElse(key);
  }

  public void addEmptyMissingDistributedByResults(
      final List<DistributedByResult> distributedByIdentityResultList,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Supplier<ViewResult> emptyViewResultSupplier) {
    context.getAllDistributedByKeysAndLabels().entrySet().stream()
        .filter(
            entry ->
                distributedByIdentityResultList.stream()
                    .noneMatch(
                        distributedByResult -> distributedByResult.getKey().equals(entry.getKey())))
        .map(
            entry ->
                createDistributedByResult(
                    entry.getKey(), entry.getValue(), emptyViewResultSupplier.get()))
        .forEach(distributedByIdentityResultList::add);
  }
}
