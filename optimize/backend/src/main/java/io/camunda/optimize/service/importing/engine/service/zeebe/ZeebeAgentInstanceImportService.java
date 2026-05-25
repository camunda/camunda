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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public class ZeebeAgentInstanceImportService
    extends ZeebeProcessInstanceSubEntityImportService<ZeebeAgentInstanceRecordDto> {

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
            .collect(
                Collectors.groupingBy(
                    zeebeRecord -> zeebeRecord.getValue().getProcessInstanceKey()))
            .values()
            .stream()
            .map(this::createProcessInstanceForData)
            .toList();
    LOG.debug(
        "Processing {} fetched zeebe agent instance records into {} process instance updates.",
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
              final OffsetDateTime recordDate = dateForTimestamp(record);

              // On every intent: update status, lastUpdatedDate, metrics, tools
              agentDto.setStatus(record.getValue().getStatus().name());
              agentDto.setLastUpdatedDate(recordDate);
              mapMetrics(agentDto, record.getValue());
              mapTools(agentDto, record.getValue());

              // On first intent: set startDate, startDateEpochMs, definition
              if (agentDto.getStartDate() == null) {
                agentDto.setStartDate(recordDate);
                agentDto.setStartDateEpochMs(record.getTimestamp());
                mapDefinition(agentDto, record.getValue());
              }

              // On terminal intent: set endDate, endDateEpochMs, totalDurationInMs
              if (record.getIntent() == AgentInstanceIntent.COMPLETED) {
                agentDto.setEndDate(recordDate);
                agentDto.setEndDateEpochMs(record.getTimestamp());
                if (agentDto.getStartDateEpochMs() != null) {
                  agentDto.setTotalDurationInMs(
                      record.getTimestamp() - agentDto.getStartDateEpochMs());
                }
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

  private void mapDefinition(
      final AgentInstanceDto agentDto, final ZeebeAgentInstanceDataDto data) {
    final AgentInstanceDto.AgentDefinitionDto definitionDto =
        new AgentInstanceDto.AgentDefinitionDto();
    definitionDto.setModel(data.getDefinition().getModel());
    definitionDto.setProvider(data.getDefinition().getProvider());
    agentDto.setDefinition(definitionDto);
  }

  private void mapMetrics(final AgentInstanceDto agentDto, final ZeebeAgentInstanceDataDto data) {
    final ZeebeAgentInstanceDataDto.AgentMetricsValueDto zeebeMetrics = data.getMetrics();
    final AgentInstanceDto.AgentMetricsDto metrics = new AgentInstanceDto.AgentMetricsDto();
    metrics.setInputTokens(zeebeMetrics.getInputTokens());
    metrics.setOutputTokens(zeebeMetrics.getOutputTokens());
    metrics.setModelCalls(zeebeMetrics.getModelCalls());
    metrics.setToolCalls(zeebeMetrics.getToolCalls());
    agentDto.setMetrics(metrics);
  }

  private void mapTools(final AgentInstanceDto agentDto, final ZeebeAgentInstanceDataDto data) {
    final List<AgentInstanceDto.AgentToolDto> tools =
        data.getTools().stream()
            .map(
                zeebeTool -> {
                  final AgentInstanceDto.AgentToolDto toolDto = new AgentInstanceDto.AgentToolDto();
                  toolDto.setName(zeebeTool.getName());
                  return toolDto;
                })
            .toList();
    agentDto.setTools(tools);
  }

  private void computeAggregateMetrics(
      final ProcessInstanceDto instance, final List<AgentInstanceDto> agentInstances) {
    long totalInputTokens = 0;
    long totalOutputTokens = 0;
    long totalModelCalls = 0;
    long totalToolCalls = 0;
    for (final AgentInstanceDto agent : agentInstances) {
      final AgentInstanceDto.AgentMetricsDto m = agent.getMetrics();
      totalInputTokens += m.getInputTokens();
      totalOutputTokens += m.getOutputTokens();
      totalModelCalls += m.getModelCalls();
      totalToolCalls += m.getToolCalls();
    }
    instance.setAgentTotalInputTokens(totalInputTokens);
    instance.setAgentTotalOutputTokens(totalOutputTokens);
    instance.setAgentTotalModelCalls(totalModelCalls);
    instance.setAgentTotalToolCalls(totalToolCalls);
    instance.setAgentTotalTokens(totalInputTokens + totalOutputTokens);
  }

  private OffsetDateTime dateForTimestamp(final ZeebeAgentInstanceRecordDto zeebeRecord) {
    return OffsetDateTime.ofInstant(
        Instant.ofEpochMilli(zeebeRecord.getTimestamp()), ZoneId.systemDefault());
  }
}
