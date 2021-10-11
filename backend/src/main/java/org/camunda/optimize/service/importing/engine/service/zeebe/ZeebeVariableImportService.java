/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.service.zeebe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import org.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import org.camunda.optimize.dto.zeebe.variable.ZeebeVariableDataDto;
import org.camunda.optimize.dto.zeebe.variable.ZeebeVariableRecordDto;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.ZeebeProcessInstanceElasticsearchImportJob;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.writer.ZeebeProcessInstanceWriter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.importing.engine.service.ImportService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.camunda.optimize.dto.optimize.ReportConstants.BOOLEAN_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.DOUBLE_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.STRING_TYPE;

@Slf4j
public class ZeebeVariableImportService implements ImportService<ZeebeVariableRecordDto> {

  private final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private final ZeebeProcessInstanceWriter processInstanceWriter;
  private final ConfigurationService configurationService;
  private final ProcessDefinitionReader processDefinitionReader;
  private final ObjectMapper objectMapper;
  private final int partitionId;

  public ZeebeVariableImportService(final ConfigurationService configurationService,
                                    final ZeebeProcessInstanceWriter processInstanceWriter,
                                    final int partitionId,
                                    final ObjectMapper objectMapper,
                                    final ProcessDefinitionReader processDefinitionReader) {
    this.elasticsearchImportJobExecutor = new ElasticsearchImportJobExecutor(
      getClass().getSimpleName(), configurationService
    );
    this.processInstanceWriter = processInstanceWriter;
    this.partitionId = partitionId;
    this.objectMapper = objectMapper;
    this.configurationService = configurationService;
    this.processDefinitionReader = processDefinitionReader;
  }

  @Override
  public void executeImport(final List<ZeebeVariableRecordDto> zeebeVariableRecords,
                            final Runnable importCompleteCallback) {
    log.trace("Importing variables from zeebe records...");

    boolean newDataIsAvailable = !zeebeVariableRecords.isEmpty();
    if (newDataIsAvailable) {
      final List<ProcessInstanceDto> newOptimizeEntities =
        mapZeebeRecordsToOptimizeEntities(zeebeVariableRecords);
      final ElasticsearchImportJob<ProcessInstanceDto> elasticsearchImportJob =
        createElasticsearchImportJob(newOptimizeEntities, importCompleteCallback);
      addElasticsearchImportJobToQueue(elasticsearchImportJob);
    }
  }

  @Override
  public ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor() {
    return elasticsearchImportJobExecutor;
  }

  private void addElasticsearchImportJobToQueue(ElasticsearchImportJob<ProcessInstanceDto> elasticsearchImportJob) {
    elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
  }

  private List<ProcessInstanceDto> mapZeebeRecordsToOptimizeEntities(
    List<ZeebeVariableRecordDto> zeebeRecords) {
    return zeebeRecords.stream()
      .collect(Collectors.groupingBy(zeebeRecord -> zeebeRecord.getValue().getProcessInstanceKey()))
      .values().stream()
      .map(this::createProcessInstanceForData)
      .collect(Collectors.toList());
  }

  private ProcessInstanceDto createProcessInstanceForData(final List<ZeebeVariableRecordDto> recordsForInstance) {
    ProcessInstanceDto instanceToAdd = createSkeletonProcessInstance(recordsForInstance);
    return updateProcessVariables(instanceToAdd, recordsForInstance);
  }

  private ProcessInstanceDto createSkeletonProcessInstance(final List<ZeebeVariableRecordDto> recordsForInstance) {
    final ZeebeVariableDataDto firstRecordValue = recordsForInstance.get(0).getValue();
    final ProcessInstanceDto processInstanceDto = new ProcessInstanceDto();


    ProcessDefinitionOptimizeDto processDefinitionOptimizeDto =
      processDefinitionReader.getProcessDefinition(String.valueOf(
        firstRecordValue.getProcessDefinitionKey())).orElseThrow(() -> new OptimizeRuntimeException(
        "The process definition with id"
          + firstRecordValue.getProcessDefinitionKey()
          + " has not yet been imported to Optimize"));
    processInstanceDto.setProcessDefinitionKey(processDefinitionOptimizeDto.getKey());
    processInstanceDto.setProcessInstanceId(String.valueOf(firstRecordValue.getProcessInstanceKey()));
    processInstanceDto.setProcessDefinitionId(String.valueOf(firstRecordValue.getProcessDefinitionKey()));
    processInstanceDto.setDataSource(new ZeebeDataSourceDto(
      configurationService.getConfiguredZeebe().getName(),
      partitionId
    ));
    return processInstanceDto;
  }

  private ProcessInstanceDto updateProcessVariables(final ProcessInstanceDto instanceToAdd,

                                                    List<ZeebeVariableRecordDto> recordsForInstance) {
    recordsForInstance = new ArrayList<>(
      recordsForInstance.stream()
        .collect(Collectors.toMap(
          ZeebeVariableRecordDto::getKey,
          Function.identity(),
          (oldVar, newVar) -> (newVar.getPosition() > oldVar.getPosition()) ? newVar : oldVar
        )).values());

    recordsForInstance
      .forEach(variableRecordDto -> {
        ZeebeVariableDataDto variableValue = variableRecordDto.getValue();
        convertToSimpleProcessVariableDto(
          variableValue,
          variableRecordDto
        ).ifPresent(variable -> {
          instanceToAdd.getVariables().add(variable);
        });
      });
    return instanceToAdd;
  }

  private Optional<SimpleProcessVariableDto> convertToSimpleProcessVariableDto(final ZeebeVariableDataDto zeebeVariableDataDto,
                                                                               final ZeebeVariableRecordDto variableRecordDto) {
    return getVariableTypeFromJsonNode(zeebeVariableDataDto, variableRecordDto.getKey()).map(type -> {
      SimpleProcessVariableDto simpleProcessVariableDto = new SimpleProcessVariableDto();
      simpleProcessVariableDto.setId(String.valueOf(variableRecordDto.getKey()));
      simpleProcessVariableDto.setName(zeebeVariableDataDto.getName());
      simpleProcessVariableDto.setVersion(variableRecordDto.getPosition());
      simpleProcessVariableDto.setType(type);
      simpleProcessVariableDto.setValue(zeebeVariableDataDto.getValue());
      if (type.equals(STRING_TYPE)) {
        simpleProcessVariableDto.setValue(stripExtraDoubleQuotationsIfExist(zeebeVariableDataDto.getValue()));
      }
      return simpleProcessVariableDto;
    });
  }

  private Optional<String> getVariableTypeFromJsonNode(final ZeebeVariableDataDto zeebeVariableDataDto,
                                                       final long recordKey) {
    try {
      final JsonNode jsonNode = objectMapper.readTree(zeebeVariableDataDto.getValue());
      final JsonNodeType jsonNodeType = jsonNode.getNodeType();
      switch (jsonNodeType) {
        case NUMBER:
          return Optional.of(DOUBLE_TYPE);
        case BOOLEAN:
          return Optional.of(BOOLEAN_TYPE);
        case STRING:
          return Optional.of(STRING_TYPE);
        default:
          return Optional.empty();
      }
    } catch (JsonProcessingException e) {
      log.debug("Could not process json node for variable record with key {}", recordKey);
      return Optional.empty();
    }
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

  private String stripExtraDoubleQuotationsIfExist(String variableValue) {
    if (variableValue.charAt(0) == '"' && variableValue.charAt(variableValue.length() - 1) == '"') {
      return variableValue.substring(1, variableValue.length() - 1);
    }
    return variableValue;
  }

}
