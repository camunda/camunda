/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import io.camunda.optimize.dto.optimize.importing.LastKpiEvaluationResultsDto;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestDto;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestRequestDto;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessUpdateDto;
import io.camunda.optimize.service.db.repository.ProcessOverviewRepository;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class ProcessOverviewWriter {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(ProcessOverviewWriter.class);
  private final ProcessOverviewRepository processOverviewRepository;

  public ProcessOverviewWriter(final ProcessOverviewRepository processOverviewRepository) {
    this.processOverviewRepository = processOverviewRepository;
  }

  public void updateProcessConfiguration(
      final String processDefinitionKey, final ProcessUpdateDto processUpdateDto) {
    final ProcessOverviewDto overviewDto =
        createNewProcessOverviewDto(processDefinitionKey, processUpdateDto);
    processOverviewRepository.updateProcessConfiguration(processDefinitionKey, overviewDto);
  }

  public void updateProcessDigestResults(
      final String processDefKey, final ProcessDigestDto processDigestDto) {
    processOverviewRepository.updateProcessDigestResults(processDefKey, processDigestDto);
  }

  public void updateProcessOwnerIfNotSet(final String processDefinitionKey, final String ownerId) {
    final ProcessUpdateDto processUpdateDto = new ProcessUpdateDto();
    processUpdateDto.setOwnerId(ownerId);
    final ProcessDigestRequestDto processDigestRequestDto = new ProcessDigestRequestDto();
    processUpdateDto.setProcessDigest(processDigestRequestDto);
    final ProcessOverviewDto processOverviewDto =
        createNewProcessOverviewDto(processDefinitionKey, processUpdateDto);
    processOverviewRepository.updateProcessOwnerIfNotSet(
        processDefinitionKey, ownerId, processOverviewDto);
  }

  public void updateKpisForProcessDefinitions(
      final Map<String, LastKpiEvaluationResultsDto> definitionKeyToKpis) {
    log.debug(
        "Updating KPI values for process definitions with keys: [{}]",
        definitionKeyToKpis.keySet());
    final List<ProcessOverviewDto> processOverviewDtos =
        definitionKeyToKpis.entrySet().stream()
            .map(
                entry -> {
                  final Map<String, String> reportIdToValue = entry.getValue().getReportIdToValue();
                  final ProcessOverviewDto processOverviewDto = new ProcessOverviewDto();
                  processOverviewDto.setProcessDefinitionKey(entry.getKey());
                  processOverviewDto.setDigest(new ProcessDigestDto(false, Collections.emptyMap()));
                  processOverviewDto.setLastKpiEvaluationResults(reportIdToValue);
                  return processOverviewDto;
                })
            .toList();
    processOverviewRepository.updateKpisForProcessDefinitions(processOverviewDtos);
  }

  public void deleteProcessOwnerEntry(final String processDefinitionKey) {
    log.info("Removing pending entry " + processDefinitionKey);
    processOverviewRepository.deleteProcessOwnerEntry(processDefinitionKey);
  }

  private ProcessOverviewDto createNewProcessOverviewDto(
      final String processDefinitionKey, final ProcessUpdateDto processUpdateDto) {
    final ProcessOverviewDto processOverviewDto = new ProcessOverviewDto();
    processOverviewDto.setProcessDefinitionKey(processDefinitionKey);
    processOverviewDto.setOwner(processUpdateDto.getOwnerId());
    processOverviewDto.setDigest(
        new ProcessDigestDto(
            processUpdateDto.getProcessDigest().isEnabled(), Collections.emptyMap()));
    processOverviewDto.setLastKpiEvaluationResults(Collections.emptyMap());
    return processOverviewDto;
  }
}
