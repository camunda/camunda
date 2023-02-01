/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.service.zeebe;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.ZeebeProcessInstanceElasticsearchImportJob;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.writer.ZeebeProcessInstanceWriter;
import org.camunda.optimize.service.importing.engine.service.ImportService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.util.List;

@Slf4j
public abstract class ZeebeProcessInstanceSubEntityImportService<T> implements ImportService<T> {

  protected final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private final ZeebeProcessInstanceWriter processInstanceWriter;
  protected final ConfigurationService configurationService;
  protected final ProcessDefinitionReader processDefinitionReader;
  protected final int partitionId;

  protected ZeebeProcessInstanceSubEntityImportService(final ConfigurationService configurationService,
                                                       final ZeebeProcessInstanceWriter processInstanceWriter,
                                                       final int partitionId,
                                                       final ProcessDefinitionReader processDefinitionReader) {
    this.elasticsearchImportJobExecutor = new ElasticsearchImportJobExecutor(
      getClass().getSimpleName(), configurationService
    );
    this.processInstanceWriter = processInstanceWriter;
    this.partitionId = partitionId;
    this.configurationService = configurationService;
    this.processDefinitionReader = processDefinitionReader;
  }

  abstract List<ProcessInstanceDto> filterAndMapZeebeRecordsToOptimizeEntities(List<T> records);

  @Override
  public void executeImport(final List<T> zeebeRecords,
                            final Runnable importCompleteCallback) {
    boolean newDataIsAvailable = !zeebeRecords.isEmpty();
    if (newDataIsAvailable) {
      final List<ProcessInstanceDto> newOptimizeEntities =
        filterAndMapZeebeRecordsToOptimizeEntities(zeebeRecords);
      final ElasticsearchImportJob<ProcessInstanceDto> elasticsearchImportJob =
        createElasticsearchImportJob(newOptimizeEntities, importCompleteCallback);
      addElasticsearchImportJobToQueue(elasticsearchImportJob);
    }
  }

  @Override
  public ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor() {
    return elasticsearchImportJobExecutor;
  }

  protected ProcessInstanceDto createSkeletonProcessInstance(final String processDefinitionKey,
                                                             final Long processInstanceId,
                                                             final Long processDefinitionId) {
    final ProcessInstanceDto processInstanceDto = new ProcessInstanceDto();
    processInstanceDto.setProcessDefinitionKey(processDefinitionKey);
    processInstanceDto.setProcessInstanceId(String.valueOf(processInstanceId));
    processInstanceDto.setProcessDefinitionId(String.valueOf(processDefinitionId));
    processInstanceDto.setDataSource(new ZeebeDataSourceDto(
      configurationService.getConfiguredZeebe().getName(),
      partitionId
    ));
    return processInstanceDto;
  }

  private void addElasticsearchImportJobToQueue(ElasticsearchImportJob<ProcessInstanceDto> elasticsearchImportJob) {
    elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
  }

  private ElasticsearchImportJob<ProcessInstanceDto> createElasticsearchImportJob(
    final List<ProcessInstanceDto> processInstanceDtos,
    final Runnable importCompleteCallback) {
    ZeebeProcessInstanceElasticsearchImportJob processInstanceImportJob =
      new ZeebeProcessInstanceElasticsearchImportJob(
        processInstanceWriter, configurationService, importCompleteCallback
      );
    processInstanceImportJob.setEntitiesToImport(processInstanceDtos);
    return processInstanceImportJob;
  }

}
