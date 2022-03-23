/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import lombok.SneakyThrows;
import org.camunda.optimize.AbstractZeebeIT;
import org.camunda.optimize.dto.optimize.ProcessInstanceConstants;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentStatus;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentType;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.dto.zeebe.ZeebeRecordDto;
import org.camunda.optimize.dto.zeebe.incident.ZeebeIncidentRecordDto;
import org.camunda.optimize.dto.zeebe.process.ZeebeProcessInstanceRecordDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.reader.ElasticsearchReaderUtil;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.persistence.incident.IncidentStatus.OPEN;
import static org.camunda.optimize.dto.optimize.persistence.incident.IncidentStatus.RESOLVED;
import static org.camunda.optimize.util.ZeebeBpmnModels.CATCH_EVENT;
import static org.camunda.optimize.util.ZeebeBpmnModels.SERVICE_TASK;
import static org.camunda.optimize.util.ZeebeBpmnModels.createIncidentProcess;
import static org.camunda.optimize.util.ZeebeBpmnModels.createSimpleServiceTaskProcess;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

public class ZeebeIncidentImportIT extends AbstractZeebeIT {

  @Test
  public void importZeebeIncidentData_openFailTaskIncident() {
    // given
    final ProcessInstanceEvent deployedInstance =
      deployAndStartInstanceForProcess(createSimpleServiceTaskProcess("someProcess"));
    zeebeExtension.failTask(SERVICE_TASK);

    // when
    waitUntilMinimumIncidentEventsExportedCount(1);
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(savedInstance -> {
        assertThat(savedInstance.getProcessInstanceId())
          .isEqualTo(String.valueOf(deployedInstance.getProcessInstanceKey()));
        assertThat(savedInstance.getProcessDefinitionId())
          .isEqualTo(String.valueOf(deployedInstance.getProcessDefinitionKey()));
        assertThat(savedInstance.getProcessDefinitionKey()).isEqualTo(deployedInstance.getBpmnProcessId());
        assertThat(savedInstance.getProcessDefinitionVersion())
          .isEqualTo(String.valueOf(deployedInstance.getVersion()));
        assertThat(savedInstance.getDataSource().getName()).isEqualTo(getConfiguredZeebeName());
        assertThat(savedInstance.getState()).isEqualTo(ProcessInstanceConstants.ACTIVE_STATE);
        assertThat(savedInstance.getTenantId()).isNull();
        assertThat(savedInstance.getBusinessKey()).isNull();
        assertThat(savedInstance.getFlowNodeInstances()).isNotEmpty();
        assertThat(savedInstance.getVariables()).isEmpty();
        assertThat(savedInstance.getStartDate()).isNotNull();
        assertThat(savedInstance.getEndDate()).isNull();
        assertThat(savedInstance.getDuration()).isNull();
        assertThat(savedInstance.getIncidents()).isNotEmpty()
          .hasSize(1)
          .containsExactly(
            createIncident(savedInstance, deployedInstance, SERVICE_TASK, OPEN)
          );
      });
  }

  @Test
  public void importZeebeIncidentData_throwErrorIncident() {
    // given
    final ProcessInstanceEvent deployedInstance =
      deployAndStartInstanceForProcess(createSimpleServiceTaskProcess("someProcess"));
    zeebeExtension.throwErrorIncident(SERVICE_TASK);

    // when
    waitUntilMinimumIncidentEventsExportedCount(1);
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(savedInstance -> {
        assertThat(savedInstance.getIncidents()).isNotEmpty()
          .hasSize(1)
          .containsExactly(
            createIncident(savedInstance, deployedInstance, SERVICE_TASK, OPEN)
          );
      });
  }

  @Test
  public void importZeebeIncidentData_missingVariableIncident() {
    // given
    final ProcessInstanceEvent deployedInstance =
      deployAndStartInstanceForProcess(createIncidentProcess("someProcess"));

    // when
    waitUntilMinimumIncidentEventsExportedCount(1);
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(savedInstance -> {
        assertThat(savedInstance.getIncidents()).isNotEmpty()
          .hasSize(1)
          .containsExactly(
            createIncident(savedInstance, deployedInstance, CATCH_EVENT, OPEN)
          );
      });
  }

  @Test
  public void importZeebeIncidentData_importResolvedIncidentInSameBatch() {
    // given
    final ProcessInstanceEvent deployedInstance =
      deployAndStartInstanceForProcess(createSimpleServiceTaskProcess("someProcess"));
    zeebeExtension.throwErrorIncident(SERVICE_TASK);
    waitUntilMinimumIncidentEventsExportedCount(1);
    resolveIncident();
    waitUntilMinimumIncidentEventsExportedCount(2);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(savedInstance -> {
        assertThat(savedInstance.getIncidents()).isNotEmpty()
          .containsExactly(
            createIncident(savedInstance, deployedInstance, SERVICE_TASK, RESOLVED)
          );
      });
  }

  @Test
  public void importZeebeIncidentData_importResolvedIncidentInDifferentBatches() {
    // given
    final ProcessInstanceEvent deployedInstance =
      deployAndStartInstanceForProcess(createSimpleServiceTaskProcess("someProcess"));
    zeebeExtension.throwErrorIncident(SERVICE_TASK);
    waitUntilMinimumIncidentEventsExportedCount(1);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(savedInstance -> {
        assertThat(savedInstance.getIncidents()).isNotEmpty()
          .containsExactly(
            createIncident(savedInstance, deployedInstance, SERVICE_TASK, OPEN)
          );
      });

    // when
    resolveIncident();
    waitUntilMinimumIncidentEventsExportedCount(2);
    importAllZeebeEntitiesFromLastIndex();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(savedInstance -> {
        assertThat(savedInstance.getIncidents()).isNotEmpty()
          .containsExactly(
            createIncident(savedInstance, deployedInstance, SERVICE_TASK, RESOLVED)
          );
      });
  }

  private void waitUntilMinimumIncidentEventsExportedCount(final int minExportedEventCount) {
    waitUntilMinimumDataExportedCount(
      minExportedEventCount,
      ElasticsearchConstants.ZEEBE_INCIDENT_INDEX_NAME,
      getQueryForIncidentEvents()
    );
  }

  private BoolQueryBuilder getQueryForIncidentEvents() {
    return boolQuery().must(termsQuery(
      ZeebeProcessInstanceRecordDto.Fields.intent,
      IncidentIntent.CREATED.name(),
      IncidentIntent.RESOLVED.name()
    ));
  }

  private IncidentDto createIncident(final ProcessInstanceDto processInstanceDto,
                                     final ProcessInstanceEvent deployedInstance,
                                     final String activityId, final IncidentStatus incidentStatus) {
    final Map<IncidentIntent, List<ZeebeIncidentRecordDto>> incidentsForRecordByIntent =
      getZeebeExportedIncidentEventsByElementId().entrySet()
        .stream()
        .flatMap(entry -> entry.getValue().stream())
        .collect(Collectors.groupingBy(ZeebeRecordDto::getIntent));
    final ZeebeIncidentRecordDto createdRecord = incidentsForRecordByIntent.get(IncidentIntent.CREATED).get(0);
    final ZeebeIncidentRecordDto resolvedRecord =
      Optional.ofNullable(incidentsForRecordByIntent.get(IncidentIntent.RESOLVED))
        .map(it -> it.get(0))
        .orElse(null);
    final IncidentDto incident = new IncidentDto();
    incident.setId(String.valueOf(createdRecord.getKey()));
    incident.setDefinitionKey(deployedInstance.getBpmnProcessId());
    incident.setDefinitionVersion(String.valueOf(deployedInstance.getVersion()));
    incident.setTenantId(null);
    incident.setProcessInstanceId(null);
    incident.setActivityId(String.valueOf(getFlowNodeIdFromProcessInstanceForActivity(
      processInstanceDto,
      activityId
    )));
    incident.setIncidentType(IncidentType.valueOfId(createdRecord.getValue().getErrorType().toString()));
    incident.setIncidentMessage(createdRecord.getValue().getErrorMessage());
    incident.setIncidentStatus(incidentStatus);
    final OffsetDateTime createTime = OffsetDateTime.ofInstant(
      Instant.ofEpochMilli(createdRecord.getTimestamp()), ZoneId.systemDefault());
    incident.setCreateTime(createTime);
    final OffsetDateTime endTime = Optional.ofNullable(resolvedRecord)
      .map(record -> OffsetDateTime.ofInstant(Instant.ofEpochMilli(record.getTimestamp()), ZoneId.systemDefault()))
      .orElse(null);
    incident.setEndTime(endTime);
    Optional.ofNullable(endTime).ifPresent(end -> incident.setDurationInMs(createTime.until(end, ChronoUnit.MILLIS)));
    return incident;
  }

  @SneakyThrows
  private Map<Long, List<ZeebeIncidentRecordDto>> getZeebeExportedIncidentEventsByElementId() {
    final String expectedIndex =
      zeebeExtension.getZeebeRecordPrefix() + "-" + ElasticsearchConstants.ZEEBE_INCIDENT_INDEX_NAME;
    final OptimizeElasticsearchClient esClient =
      elasticSearchIntegrationTestExtension.getOptimizeElasticClient();
    SearchRequest searchRequest = new SearchRequest()
      .indices(expectedIndex)
      .source(new SearchSourceBuilder()
                .query(getQueryForIncidentEvents())
                .trackTotalHits(true)
                .size(100));
    final SearchResponse searchResponse = esClient.searchWithoutPrefixing(searchRequest);
    return ElasticsearchReaderUtil.mapHits(
        searchResponse.getHits(),
        ZeebeIncidentRecordDto.class,
        embeddedOptimizeExtension.getObjectMapper()
      ).stream()
      .collect(Collectors.groupingBy(event -> event.getValue().getElementInstanceKey()));
  }

  private void resolveIncident() {
    final ZeebeIncidentRecordDto exportedIncident =
      getZeebeExportedIncidentEventsByElementId().values()
        .stream()
        .flatMap(Collection::stream)
        .findFirst().orElseThrow(() -> new OptimizeIntegrationTestException("Cannot find any exported incidents"));
    zeebeExtension.resolveIncident(exportedIncident.getValue().getJobKey(), exportedIncident.getKey());
  }

  private String getFlowNodeIdFromProcessInstanceForActivity(final ProcessInstanceDto processInstanceDto,
                                                             final String activityId) {
    return getPropertyIdFromProcessInstanceForActivity(
      processInstanceDto,
      activityId,
      FlowNodeInstanceDto::getFlowNodeId
    );
  }

}
