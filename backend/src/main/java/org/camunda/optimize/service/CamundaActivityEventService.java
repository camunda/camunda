/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.dto.optimize.query.event.activity.CamundaActivityEventDto;
import org.camunda.optimize.service.engine.importing.service.ProcessDefinitionResolverService;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.index.CamundaActivityEventIndex;
import org.camunda.optimize.service.es.writer.CamundaActivityEventWriter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.camunda.bpm.engine.ActivityTypes.*;

@AllArgsConstructor
@Component
@Slf4j
public class CamundaActivityEventService {

  public static final String START_MAPPED_SUFFIX = "start";
  public static final String END_MAPPED_SUFFIX = "end";
  public static final String PROCESS_START_TYPE = "processInstanceStart";
  public static final String PROCESS_END_TYPE = "processInstanceEnd";

  private static final Set<String> SINGLE_MAPPED_TYPES =
    // @formatter:off
    Sets.newHashSet(SUB_PROCESS, SUB_PROCESS_AD_HOC, CALL_ACTIVITY, TRANSACTION,
                    ////////////////////////////////////////////////////////////////////////////////////////////
                    BOUNDARY_TIMER, BOUNDARY_MESSAGE, BOUNDARY_SIGNAL, BOUNDARY_COMPENSATION,
                    BOUNDARY_ERROR, BOUNDARY_ESCALATION, BOUNDARY_CANCEL, BOUNDARY_CONDITIONAL,
                    ////////////////////////////////////////////////////////////////////////////////////////////
                    START_EVENT, START_EVENT_TIMER, START_EVENT_MESSAGE, START_EVENT_SIGNAL,
                    START_EVENT_ESCALATION, START_EVENT_COMPENSATION, START_EVENT_ERROR, START_EVENT_CONDITIONAL,
                    ////////////////////////////////////////////////////////////////////////////////////////////
                    INTERMEDIATE_EVENT_CATCH, INTERMEDIATE_EVENT_MESSAGE, INTERMEDIATE_EVENT_TIMER,
                    INTERMEDIATE_EVENT_LINK, INTERMEDIATE_EVENT_SIGNAL, INTERMEDIATE_EVENT_CONDITIONAL,
                    ////////////////////////////////////////////////////////////////////////////////////////////
                    INTERMEDIATE_EVENT_THROW, INTERMEDIATE_EVENT_SIGNAL_THROW, INTERMEDIATE_EVENT_COMPENSATION_THROW,
                    INTERMEDIATE_EVENT_MESSAGE_THROW, INTERMEDIATE_EVENT_NONE_THROW, INTERMEDIATE_EVENT_ESCALATION_THROW,
                    ////////////////////////////////////////////////////////////////////////////////////////////
                    END_EVENT_ERROR, END_EVENT_CANCEL, END_EVENT_TERMINATE, END_EVENT_MESSAGE,
                    END_EVENT_SIGNAL, END_EVENT_COMPENSATION, END_EVENT_ESCALATION, END_EVENT_NONE
    );
  // @formatter:on

  private static final Set<String> START_END_MAPPED_TYPES =
    Sets.newHashSet(TASK, TASK_SCRIPT, TASK_SERVICE, TASK_BUSINESS_RULE, TASK_MANUAL_TASK,
                    TASK_USER_TASK, TASK_SEND_TASK, TASK_RECEIVE_TASK
    );

  private final CamundaActivityEventWriter camundaActivityEventWriter;
  private final ProcessDefinitionResolverService processDefinitionResolverService;
  private final OptimizeElasticsearchClient elasticsearchClient;
  private final ElasticSearchSchemaManager elasticSearchSchemaManager;
  private final ConfigurationService configurationService;

  public void importRunningActivityInstancesToCamundaActivityEvents(List<FlowNodeEventDto> activityInstances) {
    importActivityInstancesToCamundaActivityEvents(activityInstances, this::convertRunningActivityToCamundaActivityEvents);
  }

  public void importCompletedActivityInstancesToCamundaActivityEvents(List<FlowNodeEventDto> activityInstances) {
    importActivityInstancesToCamundaActivityEvents(activityInstances, this::convertCompletedActivityToCamundaActivityEvents);
  }

  public void importCompletedProcessInstancesToCamundaActivityEvents(List<ProcessInstanceDto> processInstanceDtos) {
    if (configurationService.getEventBasedProcessConfiguration().isEnabled()) {
      final List<String> processDefinitionKeysInBatch = processInstanceDtos
        .stream()
        .map(ProcessInstanceDto::getProcessDefinitionKey)
        .collect(Collectors.toList());
      createMissingActivityIndicesForProcessDefinitions(processDefinitionKeysInBatch);
      final List<CamundaActivityEventDto> camundaActivityEventDtos = processInstanceDtos
        .stream()
        .flatMap(this::convertProcessInstanceToCamundaActivityEvents)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
      camundaActivityEventWriter.importActivityInstancesToCamundaActivityEvents(camundaActivityEventDtos);
    }
  }

  private static String addDelimiterForStrings(String... strings) {
    return String.join("_", strings);
  }

  private void importActivityInstancesToCamundaActivityEvents(List<FlowNodeEventDto> activityInstances,
                                                              Function<FlowNodeEventDto,
                                                                Stream<CamundaActivityEventDto>> eventExtractionFunction) {
    if (configurationService.getEventBasedProcessConfiguration().isEnabled()) {
      List<String> processDefinitionKeysInBatch = activityInstances
        .stream()
        .map(FlowNodeEventDto::getProcessDefinitionKey)
        .collect(Collectors.toList());
      createMissingActivityIndicesForProcessDefinitions(processDefinitionKeysInBatch);
      final List<CamundaActivityEventDto> camundaActivityEventDtos = activityInstances
        .stream()
        .flatMap(eventExtractionFunction)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
      camundaActivityEventWriter.importActivityInstancesToCamundaActivityEvents(camundaActivityEventDtos);
    }
  }

  private Stream<CamundaActivityEventDto> convertRunningActivityToCamundaActivityEvents(FlowNodeEventDto flowNodeEventDto) {
    if (START_END_MAPPED_TYPES.contains(flowNodeEventDto.getActivityType())) {
      return Stream.of(
        toCamundaActivityEvent(flowNodeEventDto).toBuilder()
          .activityId(addDelimiterForStrings(flowNodeEventDto.getActivityId(), START_MAPPED_SUFFIX))
          .activityName(addDelimiterForStrings(flowNodeEventDto.getActivityName(), START_MAPPED_SUFFIX))
          .activityInstanceId(addDelimiterForStrings(flowNodeEventDto.getActivityInstanceId(), START_MAPPED_SUFFIX))
          .build()
      );
    }
    return Stream.empty();
  }

  private Stream<CamundaActivityEventDto> convertCompletedActivityToCamundaActivityEvents(FlowNodeEventDto flowNodeEventDto) {
    if (START_END_MAPPED_TYPES.contains(flowNodeEventDto.getActivityType())) {
      return Stream.of(
        toCamundaActivityEvent(flowNodeEventDto).toBuilder()
          .activityId(addDelimiterForStrings(flowNodeEventDto.getActivityId(), START_MAPPED_SUFFIX))
          .activityName(addDelimiterForStrings(flowNodeEventDto.getActivityName(), START_MAPPED_SUFFIX))
          .activityInstanceId(addDelimiterForStrings(flowNodeEventDto.getActivityInstanceId(), START_MAPPED_SUFFIX))
          .build(),
        toCamundaActivityEvent(flowNodeEventDto).toBuilder()
          .activityId(addDelimiterForStrings(flowNodeEventDto.getActivityId(), END_MAPPED_SUFFIX))
          .activityName(addDelimiterForStrings(flowNodeEventDto.getActivityName(), END_MAPPED_SUFFIX))
          .activityInstanceId(addDelimiterForStrings(flowNodeEventDto.getActivityInstanceId(), END_MAPPED_SUFFIX))
          .timestamp(flowNodeEventDto.getEndDate())
          .build()
      );
    } else if (SINGLE_MAPPED_TYPES.contains(flowNodeEventDto.getActivityType())) {
      return Stream.of(toCamundaActivityEvent(flowNodeEventDto).toBuilder()
                         .timestamp(flowNodeEventDto.getStartDate())
                         .build());
    }
    return Stream.empty();
  }

  private CamundaActivityEventDto toCamundaActivityEvent(final FlowNodeEventDto flowNodeEventDto) {
    Optional<ProcessDefinitionOptimizeDto> processDefinition =
      processDefinitionResolverService.getDefinitionForProcessDefinitionId(
        flowNodeEventDto.getProcessDefinitionId());
    return CamundaActivityEventDto.builder()
      .activityId(flowNodeEventDto.getActivityId())
      .activityName(flowNodeEventDto.getActivityName())
      .activityType(flowNodeEventDto.getActivityType())
      .activityInstanceId(flowNodeEventDto.getId())
      .processDefinitionKey(flowNodeEventDto.getProcessDefinitionKey())
      .processInstanceId(flowNodeEventDto.getProcessInstanceId())
      .processDefinitionVersion(processDefinition.map(ProcessDefinitionOptimizeDto::getVersion).orElse(null))
      .processDefinitionName(processDefinition.map(ProcessDefinitionOptimizeDto::getName).orElse(null))
      .engine(flowNodeEventDto.getEngineAlias())
      .tenantId(flowNodeEventDto.getTenantId())
      .timestamp(flowNodeEventDto.getStartDate())
      .build();
  }

  private Stream<CamundaActivityEventDto> convertProcessInstanceToCamundaActivityEvents(ProcessInstanceDto
                                                                                          processInstanceDto) {
    String processDefinitionName = processDefinitionResolverService.getDefinitionForProcessDefinitionId(
      processInstanceDto.getProcessDefinitionId()).map(DefinitionOptimizeDto::getName).orElse(null);
    return Stream.of(
      CamundaActivityEventDto.builder()
        .activityId(addDelimiterForStrings(processInstanceDto.getProcessDefinitionKey(), PROCESS_START_TYPE))
        .activityName(PROCESS_START_TYPE)
        .activityType(PROCESS_START_TYPE)
        .activityInstanceId(addDelimiterForStrings(processInstanceDto.getProcessDefinitionKey(), PROCESS_START_TYPE))
        .processDefinitionKey(processInstanceDto.getProcessDefinitionKey())
        .processInstanceId(processInstanceDto.getProcessInstanceId())
        .processDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
        .processDefinitionName(processDefinitionName)
        .engine(processInstanceDto.getEngine())
        .tenantId(processInstanceDto.getTenantId())
        .timestamp(processInstanceDto.getStartDate())
        .build(),
      CamundaActivityEventDto.builder()
        .activityId(addDelimiterForStrings(processInstanceDto.getProcessDefinitionKey(), PROCESS_END_TYPE))
        .activityName(PROCESS_END_TYPE)
        .activityType(PROCESS_END_TYPE)
        .activityInstanceId(addDelimiterForStrings(processInstanceDto.getProcessDefinitionKey(), PROCESS_END_TYPE))
        .processDefinitionKey(processInstanceDto.getProcessDefinitionKey())
        .processInstanceId(processInstanceDto.getProcessInstanceId())
        .processDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
        .processDefinitionName(processDefinitionName)
        .engine(processInstanceDto.getEngine())
        .tenantId(processInstanceDto.getTenantId())
        .timestamp(processInstanceDto.getEndDate())
        .build()
    );
  }

  private void createMissingActivityIndicesForProcessDefinitions(List<String> processDefinitionKeys) {
    final List<IndexMappingCreator> activityIndicesToCheck = processDefinitionKeys.stream()
      .distinct()
      .map(CamundaActivityEventIndex::new)
      .collect(Collectors.toList());
    try {
      // We make this check first to see if we can avoid checking individually for each definition key in the batch
      if (elasticSearchSchemaManager.indicesExist(elasticsearchClient, activityIndicesToCheck)) {
        return;
      }
    } catch (OptimizeRuntimeException ex) {
      log.warn(
        "Failed to check if camunda activity event indices exist for process definition keys {}",
        processDefinitionKeys
      );
    }
    activityIndicesToCheck.forEach(activityIndex -> {
      try {
        final boolean indexAlreadyExists = elasticSearchSchemaManager.indexExists(
          elasticsearchClient, activityIndex
        );
        if (!indexAlreadyExists) {
          elasticSearchSchemaManager.createOptimizeIndex(
            elasticsearchClient, activityIndex);
        }
      } catch (final Exception e) {
        log.error("Failed ensuring camunda activity event index is present: {}", activityIndex.getIndexName(), e);
        throw e;
      }
    });
  }

}
