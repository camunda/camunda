/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.service.zeebe;

import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import org.camunda.optimize.dto.zeebe.definition.ZeebeProcessDefinitionDataDto;
import org.camunda.optimize.dto.zeebe.definition.ZeebeProcessDefinitionRecordDto;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.ProcessDefinitionElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.importing.engine.service.ImportService;
import org.camunda.optimize.service.util.BpmnModelUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class ZeebeProcessDefinitionImportService implements ImportService<ZeebeProcessDefinitionRecordDto> {

  private static final Set<ProcessIntent> INTENTS_TO_IMPORT = Set.of(ProcessIntent.CREATED);

  private final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private final ProcessDefinitionWriter processDefinitionWriter;
  private final ConfigurationService configurationService;
  private final int partitionId;

  public ZeebeProcessDefinitionImportService(final ConfigurationService configurationService,
                                             final ProcessDefinitionWriter processDefinitionWriter,
                                             final int partitionId) {
    this.elasticsearchImportJobExecutor = new ElasticsearchImportJobExecutor(
      getClass().getSimpleName(), configurationService
    );
    this.processDefinitionWriter = processDefinitionWriter;
    this.partitionId = partitionId;
    this.configurationService = configurationService;
  }

  @Override
  public void executeImport(final List<ZeebeProcessDefinitionRecordDto> pageOfProcessDefinitions,
                            final Runnable importCompleteCallback) {
    log.trace("Importing process definitions from zeebe records...");

    boolean newDataIsAvailable = !pageOfProcessDefinitions.isEmpty();
    if (newDataIsAvailable) {
      final List<ProcessDefinitionOptimizeDto> newOptimizeEntities =
        filterAndMapZeebeRecordsToOptimizeEntities(pageOfProcessDefinitions);
      final ElasticsearchImportJob<ProcessDefinitionOptimizeDto> elasticsearchImportJob =
        createElasticsearchImportJob(newOptimizeEntities, importCompleteCallback);
      addElasticsearchImportJobToQueue(elasticsearchImportJob);
    }
  }

  @Override
  public ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor() {
    return elasticsearchImportJobExecutor;
  }

  private void addElasticsearchImportJobToQueue(ElasticsearchImportJob<ProcessDefinitionOptimizeDto> elasticsearchImportJob) {
    elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
  }

  private List<ProcessDefinitionOptimizeDto> filterAndMapZeebeRecordsToOptimizeEntities(List<ZeebeProcessDefinitionRecordDto> zeebeRecords) {
    final List<ProcessDefinitionOptimizeDto> optimizeDtos = zeebeRecords
      .stream()
      .filter(zeebeRecord -> INTENTS_TO_IMPORT.contains(zeebeRecord.getIntent()))
      .map(this::mapZeebeRecordsToOptimizeEntities)
      .collect(Collectors.toList());
    log.debug(
      "Processing {} fetched zeebe process definition records, of which {} are relevant to Optimize and will be imported.",
      zeebeRecords.size(),
      optimizeDtos.size()
    );
    return optimizeDtos;
  }

  private ElasticsearchImportJob<ProcessDefinitionOptimizeDto> createElasticsearchImportJob(
    final List<ProcessDefinitionOptimizeDto> processDefinitions,
    final Runnable importCompleteCallback) {
    ProcessDefinitionElasticsearchImportJob procDefImportJob = new ProcessDefinitionElasticsearchImportJob(
      processDefinitionWriter, importCompleteCallback
    );
    procDefImportJob.setEntitiesToImport(processDefinitions);
    return procDefImportJob;
  }

  private ProcessDefinitionOptimizeDto mapZeebeRecordsToOptimizeEntities(ZeebeProcessDefinitionRecordDto zeebeProcessDefinitionRecord) {
    final ZeebeProcessDefinitionDataDto recordData = zeebeProcessDefinitionRecord.getValue();
    String bpmn = new String(recordData.getResource(), StandardCharsets.UTF_8);
    return ProcessDefinitionOptimizeDto.builder()
      .id(String.valueOf(recordData.getProcessDefinitionKey()))
      .key(String.valueOf(recordData.getBpmnProcessId()))
      .version(String.valueOf(recordData.getVersion()))
      .versionTag(null)
      .name(BpmnModelUtil.extractProcessDefinitionName(String.valueOf(recordData.getBpmnProcessId()), bpmn)
              .orElse(recordData.getBpmnProcessId()))
      .bpmn20Xml(bpmn)
      .dataSource(new ZeebeDataSourceDto(configurationService.getConfiguredZeebe().getName(), partitionId))
      .tenantId(null)
      .deleted(false)
      .flowNodeData(BpmnModelUtil.extractFlowNodeData(bpmn))
      .build();
  }

}
