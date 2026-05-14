/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service.zeebe;

import static io.camunda.optimize.service.db.DatabaseConstants.ZEEBE_AGENT_INSTANCE_INDEX_NAME;

import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.process.AgentInstanceDto;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceDataDto;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceRecordDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.writer.ProcessInstanceWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public class ZeebeAgentInstanceImportService
    extends ZeebeProcessInstanceSubEntityImportService<ZeebeAgentInstanceRecordDto> {

  private static final Set<AgentInstanceIntent> INTENTS_TO_IMPORT =
      Set.of(AgentInstanceIntent.CREATED, AgentInstanceIntent.COMPLETED);
  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ZeebeAgentInstanceImportService.class);

  public ZeebeAgentInstanceImportService(
      final ConfigurationService configurationService,
      final ProcessInstanceWriter processInstanceWriter,
      final int partitionId,
      final ProcessDefinitionReader processDefinitionReader,
      final DatabaseClient databaseClient) {
    super(
        configurationService,
        processInstanceWriter,
        partitionId,
        processDefinitionReader,
        databaseClient,
        ZEEBE_AGENT_INSTANCE_INDEX_NAME);
  }

  @Override
  protected List<ProcessInstanceDto> filterAndMapZeebeRecordsToOptimizeEntities(
      final List<ZeebeAgentInstanceRecordDto> zeebeRecords) {
    final List<ProcessInstanceDto> optimizeDtos =
        zeebeRecords.stream()
            .filter(zeebeRecord -> INTENTS_TO_IMPORT.contains(zeebeRecord.getIntent()))
            .collect(
                Collectors.groupingBy(
                    zeebeRecord -> zeebeRecord.getValue().getProcessInstanceKey()))
            .values()
            .stream()
            .map(this::createProcessInstanceForData)
            .collect(Collectors.toList());
    LOG.debug(
        "Processing {} fetched zeebe agent instance records, of which {} are relevant to Optimize and will be imported.",
        zeebeRecords.size(),
        optimizeDtos.size());
    return optimizeDtos;
  }

  private ProcessInstanceDto createProcessInstanceForData(
      final List<ZeebeAgentInstanceRecordDto> recordsForInstance) {
    final ZeebeAgentInstanceDataDto firstRecordValue = recordsForInstance.get(0).getValue();
    final ProcessInstanceDto instanceToAdd =
        createSkeletonProcessInstance(
            firstRecordValue.getBpmnProcessId(),
            firstRecordValue.getProcessInstanceKey(),
            firstRecordValue.getProcessDefinitionKey(),
            firstRecordValue.getTenantId());
    return updateAgentInstances(instanceToAdd, recordsForInstance);
  }

  private ProcessInstanceDto updateAgentInstances(
      final ProcessInstanceDto instanceToAdd,
      final List<ZeebeAgentInstanceRecordDto> recordsForInstance) {
    final Map<Long, AgentInstanceDto> agentsByKey = new HashMap<>();
    recordsForInstance.stream()
        .sorted(Comparator.comparingLong(ZeebeAgentInstanceRecordDto::getTimestamp))
        .forEach(
            record -> {
              final long agentKey = record.getKey();
              final AgentInstanceDto agentDto =
                  agentsByKey.getOrDefault(agentKey, createSkeletonAgentInstance(record));
              if (record.getIntent() == AgentInstanceIntent.CREATED) {
                agentDto.setStartDate(dateForTimestamp(record));
                agentDto.setStartDateEpochMs(record.getTimestamp());
              } else if (record.getIntent() == AgentInstanceIntent.COMPLETED) {
                agentDto.setEndDate(dateForTimestamp(record));
                mapMetrics(agentDto, record.getValue());
                mapTools(agentDto, record.getValue());
              }
              agentsByKey.put(agentKey, agentDto);
            });

    final List<AgentInstanceDto> agentInstances = new ArrayList<>(agentsByKey.values());
    instanceToAdd.setAgentInstances(agentInstances);
    computeAggregateMetrics(instanceToAdd, agentInstances);
    return instanceToAdd;
  }

  private AgentInstanceDto createSkeletonAgentInstance(final ZeebeAgentInstanceRecordDto record) {
    final ZeebeAgentInstanceDataDto data = record.getValue();
    final AgentInstanceDto dto = new AgentInstanceDto();
    dto.setAgentInstanceId(String.valueOf(record.getKey()));
    dto.setFlowNodeId(data.getElementId());
    dto.setProcessDefinitionVersion(String.valueOf(data.getProcessDefinitionVersion()));
    return dto;
  }

  private void mapMetrics(final AgentInstanceDto agentDto, final ZeebeAgentInstanceDataDto data) {
    final ZeebeAgentInstanceDataDto.AgentMetricsValueDto zeebeMetrics = data.getMetrics();
    final AgentInstanceDto.AgentMetricsDto metrics = new AgentInstanceDto.AgentMetricsDto();
    metrics.setInputTokens(zeebeMetrics.getInputTokens());
    metrics.setOutputTokens(zeebeMetrics.getOutputTokens());
    metrics.setModelCalls((long) zeebeMetrics.getModelCalls());
    metrics.setToolCalls((long) zeebeMetrics.getToolCalls());
    agentDto.setMetrics(metrics);
  }

  private void mapTools(final AgentInstanceDto agentDto, final ZeebeAgentInstanceDataDto data) {
    final List<AgentInstanceDto.AgentToolDto> tools =
        data.getTools().stream()
            .map(
                (AgentInstanceRecordValue.AgentInstanceToolValue zeebeTool) -> {
                  final AgentInstanceDto.AgentToolDto toolDto = new AgentInstanceDto.AgentToolDto();
                  toolDto.setName(zeebeTool.getName());
                  return toolDto;
                })
            .collect(Collectors.toList());
    agentDto.setTools(tools);
  }

  private void computeAggregateMetrics(
      final ProcessInstanceDto instance, final List<AgentInstanceDto> agentInstances) {
    final long totalInputTokens =
        agentInstances.stream()
            .mapToLong(
                a -> a.getMetrics().getInputTokens() != null ? a.getMetrics().getInputTokens() : 0L)
            .sum();
    final long totalOutputTokens =
        agentInstances.stream()
            .mapToLong(
                a ->
                    a.getMetrics().getOutputTokens() != null
                        ? a.getMetrics().getOutputTokens()
                        : 0L)
            .sum();
    final long totalModelCalls =
        agentInstances.stream()
            .mapToLong(
                a -> a.getMetrics().getModelCalls() != null ? a.getMetrics().getModelCalls() : 0L)
            .sum();
    final long totalToolCalls =
        agentInstances.stream()
            .mapToLong(
                a -> a.getMetrics().getToolCalls() != null ? a.getMetrics().getToolCalls() : 0L)
            .sum();
    instance.setAgentTotalInputTokens(totalInputTokens);
    instance.setAgentTotalOutputTokens(totalOutputTokens);
    instance.setAgentTotalModelCalls(totalModelCalls);
    instance.setAgentTotalToolCalls(totalToolCalls);
  }

  private OffsetDateTime dateForTimestamp(final ZeebeAgentInstanceRecordDto zeebeRecord) {
    return OffsetDateTime.ofInstant(
        Instant.ofEpochMilli(zeebeRecord.getTimestamp()), ZoneId.systemDefault());
  }
}
