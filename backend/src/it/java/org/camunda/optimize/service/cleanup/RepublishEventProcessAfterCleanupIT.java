/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.cleanup;

import com.google.common.collect.ImmutableMap;
import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessInstanceDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.importing.eventprocess.AbstractEventProcessIT;
import org.camunda.optimize.service.util.configuration.cleanup.CleanupConfiguration;
import org.camunda.optimize.service.util.configuration.cleanup.CleanupMode;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.applyCamundaTaskStartEventSuffix;

public class RepublishEventProcessAfterCleanupIT extends AbstractEventProcessIT {
  private static final String VARIABLE_NAME = "var";

  @Test
  public void republishAfterEngineDataCleanupModeAll() {
    // given
    getCleanupConfiguration().getProcessDataCleanupConfiguration().setEnabled(true);
    getCleanupConfiguration().getProcessDataCleanupConfiguration().setCleanupMode(CleanupMode.ALL);
    final String instanceIdToKeep = "trace1";
    final ProcessInstanceEngineDto engineInstance = deployAndStartInstanceWithBusinessKey(instanceIdToKeep);
    final String instanceIdToCleanup = "trace2";
    startNewProcessInstanceWithBusinessKeyAndEndTimeLessThanTtl(engineInstance, instanceIdToCleanup);

    importAllEngineEntitiesFromScratch();

    final String eventProcessMapping = publishEventMappingUsingProcessInstanceCamundaEvents(
      engineInstance,
      createMappingsForEventProcess(
        engineInstance, BPMN_START_EVENT_ID, applyCamundaTaskStartEventSuffix(USER_TASK_ID_ONE), BPMN_END_EVENT_ID
      )
    );
    executeImportCycle();

    // when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    republishEventProcess(eventProcessMapping);

    // then
    final String eventProcessPublishState = getEventPublishStateIdForEventProcessMappingId(eventProcessMapping);
    final List<EventProcessInstanceDto> eventProcessInstances =
      getEventProcessInstancesFromElasticsearchForProcessPublishStateId(eventProcessPublishState);

    assertThat(eventProcessInstances)
      .extracting(ProcessInstanceDto::getProcessInstanceId)
      .containsExactly(instanceIdToKeep);
  }

  @Test
  public void republishAfterEngineDataCleanupModeVariablesTracedByBusinessKey() {
    // given
    getCleanupConfiguration().getProcessDataCleanupConfiguration().setEnabled(true);
    getCleanupConfiguration().getProcessDataCleanupConfiguration().setCleanupMode(CleanupMode.VARIABLES);
    final Map<String, Object> variables = ImmutableMap.of(VARIABLE_NAME, "1");
    final String instanceIdWithAllVariables = "trace1";
    final ProcessInstanceEngineDto engineInstance = deployAndStartInstanceWithBusinessKey(
      instanceIdWithAllVariables, variables
    );
    final String instanceIdWithCleanedVariables = "trace2";
    startNewProcessInstanceWithBusinessKeyAndEndTimeLessThanTtl(
      engineInstance, instanceIdWithCleanedVariables, variables
    );

    importAllEngineEntitiesFromScratch();

    final String eventProcessMapping = publishEventMappingUsingProcessInstanceCamundaEvents(
      engineInstance,
      createMappingsForEventProcess(
        engineInstance, BPMN_START_EVENT_ID, applyCamundaTaskStartEventSuffix(USER_TASK_ID_ONE), BPMN_END_EVENT_ID
      )
    );
    executeImportCycle();

    // when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    republishEventProcess(eventProcessMapping);

    // then
    final String eventProcessPublishState = getEventPublishStateIdForEventProcessMappingId(eventProcessMapping);
    final List<EventProcessInstanceDto> eventProcessInstances =
      getEventProcessInstancesFromElasticsearchForProcessPublishStateId(eventProcessPublishState);

    assertThat(eventProcessInstances)
      .extracting(ProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(instanceIdWithAllVariables, instanceIdWithCleanedVariables);
    assertThat(eventProcessInstances)
      .allSatisfy(eventProcessInstanceDto -> {
        if (instanceIdWithAllVariables.equals(eventProcessInstanceDto.getProcessInstanceId())) {
          assertThat(eventProcessInstanceDto.getVariables()).isNotEmpty();
        } else if (instanceIdWithCleanedVariables.equals(eventProcessInstanceDto.getProcessInstanceId())) {
          assertThat(eventProcessInstanceDto.getVariables()).isEmpty();
        }
      });
  }

  @Test
  public void republishAfterEngineDataCleanupModeVariablesTracedByVariable() {
    // given
    getCleanupConfiguration().getProcessDataCleanupConfiguration().setEnabled(true);
    getCleanupConfiguration().getProcessDataCleanupConfiguration().setCleanupMode(CleanupMode.VARIABLES);
    final String instanceIdWithAllVariables = "trace1";
    final ProcessInstanceEngineDto engineInstance = deployAndStartProcessWithVariables(
      ImmutableMap.of(VARIABLE_NAME, instanceIdWithAllVariables)
    );
    final String instanceIdWithCleanedVariables = "trace2";
    startNewProcessInstanceWithEndTimeLessThanTtlAndVariables(
      engineInstance, ImmutableMap.of(VARIABLE_NAME, instanceIdWithCleanedVariables)
    );

    importAllEngineEntitiesFromScratch();

    final String eventProcessMapping = publishEventMappingUsingProcessInstanceCamundaEventsAndTraceVariable(
      engineInstance,
      createMappingsForEventProcess(
        engineInstance, BPMN_START_EVENT_ID, applyCamundaTaskStartEventSuffix(USER_TASK_ID_ONE), BPMN_END_EVENT_ID
      ),
      VARIABLE_NAME
    );
    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();
    executeImportCycle();

    // when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    republishEventProcess(eventProcessMapping);

    // then
    final String eventProcessPublishState = getEventPublishStateIdForEventProcessMappingId(eventProcessMapping);
    final List<EventProcessInstanceDto> eventProcessInstances =
      getEventProcessInstancesFromElasticsearchForProcessPublishStateId(eventProcessPublishState);

    assertThat(eventProcessInstances)
      .extracting(ProcessInstanceDto::getProcessInstanceId)
      .containsExactly(instanceIdWithAllVariables);
  }

  @Test
  public void republishAfterIngestedEventCleanup() {
    // given
    getCleanupConfiguration().getIngestedEventCleanupConfiguration().setEnabled(true);
    final String instanceIdToCleanup = "trace1";
    ingestTestEvent(STARTED_EVENT, getEndTimeLessThanGlobalTtl(), instanceIdToCleanup);
    ingestTestEvent(FINISHED_EVENT, getEndTimeLessThanGlobalTtl(), instanceIdToCleanup);
    final String instanceIdToKeep = "trace2";
    ingestTestEvent(STARTED_EVENT, OffsetDateTime.now(), instanceIdToKeep);
    ingestTestEvent(FINISHED_EVENT, OffsetDateTime.now(), instanceIdToKeep);

    final String eventProcessMapping = createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);
    publishEventProcess(eventProcessMapping);

    // when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    republishEventProcess(eventProcessMapping);

    // then
    final String eventProcessPublishState = getEventPublishStateIdForEventProcessMappingId(eventProcessMapping);
    final List<EventProcessInstanceDto> eventProcessInstances =
      getEventProcessInstancesFromElasticsearchForProcessPublishStateId(eventProcessPublishState);

    assertThat(eventProcessInstances)
      .extracting(ProcessInstanceDto::getProcessInstanceId)
      .containsExactly(instanceIdToKeep);
  }


  private void startNewProcessInstanceWithBusinessKeyAndEndTimeLessThanTtl(final ProcessInstanceEngineDto instance,
                                                                           final String businessKey) {
    startNewProcessInstanceWithBusinessKeyAndEndTimeLessThanTtl(instance, businessKey, Collections.emptyMap());
  }

  private void startNewProcessInstanceWithEndTimeLessThanTtlAndVariables(final ProcessInstanceEngineDto instance,
                                                                         final Map<String, Object> variables) {
    startNewProcessInstanceWithBusinessKeyAndEndTimeLessThanTtl(instance, null, variables);
  }

  @SneakyThrows
  private void startNewProcessInstanceWithBusinessKeyAndEndTimeLessThanTtl(final ProcessInstanceEngineDto instance,
                                                                           final String businessKey,
                                                                           final Map<String, Object> variables) {
    final ProcessInstanceEngineDto newEngineInstance = startNewProcessInstanceWithBusinessKey(
      instance.getDefinitionId(), businessKey, variables
    );
    engineDatabaseExtension.changeProcessInstanceEndDate(newEngineInstance.getId(), getEndTimeLessThanGlobalTtl());
  }

  private ProcessInstanceEngineDto startNewProcessInstanceWithBusinessKey(final String definitionId,
                                                                          final String businessKey,
                                                                          final Map<String, Object> variables) {
    return engineIntegrationExtension.startProcessInstance(definitionId, variables, businessKey);
  }

  private OffsetDateTime getEndTimeLessThanGlobalTtl() {
    return OffsetDateTime.now().minus(getCleanupConfiguration().getTtl()).minusSeconds(1);
  }

  private CleanupConfiguration getCleanupConfiguration() {
    return embeddedOptimizeExtension.getConfigurationService().getCleanupServiceConfiguration();
  }
}
