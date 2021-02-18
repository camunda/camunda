/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate33To34;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.query.event.process.EventImportSourceDto;
import org.camunda.optimize.dto.optimize.query.event.process.es.EsEventProcessPublishStateDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceConfigDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceConfigDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceType;
import org.camunda.optimize.dto.optimize.query.event.process.source.ExternalEventSourceConfigDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.es.schema.index.events.EventProcessPublishStateIndex;
import org.camunda.optimize.upgrade.migrate33To34.dto.EsEventProcessPublishStateDtoV3Old;
import org.camunda.optimize.upgrade.migrate33To34.dto.EventImportSourceDtoOld;
import org.camunda.optimize.upgrade.migrate33To34.dto.EventSourceEntryDtoOld;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.factories.Upgrade33To34PlanFactory;
import org.camunda.optimize.util.SuppressionConstants;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.es.schema.index.events.EventProcessPublishStateIndex.EVENT_IMPORT_SOURCES;
import static org.camunda.optimize.service.es.schema.index.events.EventProcessPublishStateIndex.EVENT_IMPORT_SOURCE_TYPE;
import static org.camunda.optimize.upgrade.migrate33To34.indices.EventProcessPublishStateIndexV3Old.EVENT_SOURCE_TYPE;

public class MigrateEventPublishStateEventSourcesIT extends AbstractUpgrade33IT {

  @SneakyThrows
  @Test
  public void eventProcessPublishStateEventSourcesAreMigratedToNewDataStructure() {
    // given
    executeBulk("steps/3.3/publishstates/33-event-publish-states-with-event-sources.json");
    final UpgradePlan upgradePlan = new Upgrade33To34PlanFactory().createUpgradePlan();

    // then
    final List<EsEventProcessPublishStateDtoV3Old> publishStatesBeforeUpgrade = getAllDocumentsOfIndexAs(
      EVENT_PUBLISH_STATE_INDEX.getIndexName(),
      EsEventProcessPublishStateDtoV3Old.class
    );

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    final SearchHit[] publishStateHitsAfterUpgrade =
      getAllDocumentsOfIndex(new EventProcessPublishStateIndex().getIndexName());
    assertThat(publishStateHitsAfterUpgrade).hasSize(3)
      .allSatisfy(publishState -> {
        final EsEventProcessPublishStateDtoV3Old beforeUpgrade = getPublishStateBeforeUpgrade(
          publishStatesBeforeUpgrade, publishState.getId());
        assertImportSourcesHaveBeenUpgraded(beforeUpgrade, publishState);
        assertOtherPublishStatePropertiesAreUnaffected(beforeUpgrade);
      });
  }

  private void assertOtherPublishStatePropertiesAreUnaffected(final EsEventProcessPublishStateDtoV3Old beforeUpgrade) {
    final EsEventProcessPublishStateDto afterUpgrade = getDocumentOfIndexByIdAs(
      new EventProcessPublishStateIndex().getIndexName(),
      beforeUpgrade.getId(),
      EsEventProcessPublishStateDto.class
    ).orElseThrow(() -> new OptimizeIntegrationTestException(
      "Cannot fetch event process publish state with ID: " + beforeUpgrade.getId()));
    assertThat(beforeUpgrade.getId()).isEqualTo(afterUpgrade.getId());
    assertThat(beforeUpgrade.getProcessMappingId()).isEqualTo(afterUpgrade.getProcessMappingId());
    assertThat(beforeUpgrade.getName()).isEqualTo(afterUpgrade.getName());
    assertThat(beforeUpgrade.getPublishDateTime()).isEqualTo(afterUpgrade.getPublishDateTime());
    assertThat(beforeUpgrade.getState()).isEqualTo(afterUpgrade.getState());
    assertThat(beforeUpgrade.getPublishProgress()).isEqualTo(afterUpgrade.getPublishProgress());
    assertThat(beforeUpgrade.getDeleted()).isEqualTo(afterUpgrade.getDeleted());
    assertThat(beforeUpgrade.getXml()).isEqualTo(afterUpgrade.getXml());
  }

  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  private void assertImportSourcesHaveBeenUpgraded(final EsEventProcessPublishStateDtoV3Old publishStateBeforeUpgrade,
                                                   final SearchHit publishState) {
    final List<Map<String, Object>> importSources =
      (List<Map<String, Object>>) publishState.getSourceAsMap().get(EVENT_IMPORT_SOURCES);
    assertThat(importSources).hasSameSizeAs(publishStateBeforeUpgrade.getEventImportSources());
    importSources.forEach(importSource -> {
      List<Map<String, Object>> eventSourceConfigurations =
        (List<Map<String, Object>>) importSource.get(EventImportSourceDto.Fields.eventSourceConfigurations);
      assertThat(eventSourceConfigurations).hasSize(1);
      final Map<String, Object> eventSourceConfig = eventSourceConfigurations.get(0);
      EventImportSourceDtoOld sourceBeforeUpgrade = findEventImportSourceBeforeUpgrade(
        publishStateBeforeUpgrade,
        eventSourceConfig,
        EventSourceType.valueOf(((String) importSource.get(EVENT_IMPORT_SOURCE_TYPE)).toUpperCase())
      );
      final EventSourceEntryDtoOld oldEventSource = sourceBeforeUpgrade.getEventSource();
      assertThat(oldEventSource.getType().getId()).isEqualTo(importSource.get(EVENT_IMPORT_SOURCE_TYPE));
      assertThat(oldEventSource.getEventScope().stream()
                   .map(EventScopeType::getId)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrderElementsOf(
          ((List<String>) eventSourceConfig.get(EventSourceConfigDto.Fields.eventScope)));
      if (EventSourceType.EXTERNAL.getId().equals(eventSourceConfig.get(EVENT_SOURCE_TYPE))) {
        assertThat(eventSourceConfig).containsEntry(ExternalEventSourceConfigDto.Fields.includeAllGroups, true);
        assertThat(eventSourceConfig.get(ExternalEventSourceConfigDto.Fields.group)).isNull();
      } else if (EventSourceType.CAMUNDA.getId()
        .equals(eventSourceConfig.get(EVENT_SOURCE_TYPE))) {
        assertThat(eventSourceConfig).containsEntry(
          CamundaEventSourceConfigDto.Fields.processDefinitionKey, oldEventSource.getProcessDefinitionKey());
        assertThat(eventSourceConfig.get(CamundaEventSourceConfigDto.Fields.processDefinitionName)).isNull();
        assertThat(eventSourceConfig).containsEntry(
          CamundaEventSourceConfigDto.Fields.tracedByBusinessKey, oldEventSource.isTracedByBusinessKey());
        assertThat(eventSourceConfig).containsEntry(
          CamundaEventSourceConfigDto.Fields.traceVariable, oldEventSource.getTraceVariable());
        assertThat(eventSourceConfig).containsEntry(
          CamundaEventSourceConfigDto.Fields.tenants, oldEventSource.getTenants());
        assertThat(eventSourceConfig).containsEntry(
          CamundaEventSourceConfigDto.Fields.versions, oldEventSource.getVersions());
      }
      assertThat(importSource.get(EventImportSourceDtoOld.Fields.eventSource)).isNull();
    });
  }

  private EventImportSourceDtoOld findEventImportSourceBeforeUpgrade(final EsEventProcessPublishStateDtoV3Old publishStateBeforeUpgrade,
                                                                     final Map<String, Object> eventSourceConfig,
                                                                     final EventSourceType sourceType) {
    if (EventSourceType.EXTERNAL.equals(sourceType)) {
      return publishStateBeforeUpgrade.getEventImportSources()
        .stream()
        .filter(importSource -> EventSourceType.EXTERNAL.equals(importSource.getEventSource().getType()))
        .findFirst()
        .get();
    } else if (EventSourceType.CAMUNDA.equals(sourceType)) {
      return publishStateBeforeUpgrade.getEventImportSources()
        .stream()
        .filter(importSource -> EventSourceType.CAMUNDA.equals(importSource.getEventSource().getType())
          && importSource.getEventSource().getProcessDefinitionKey()
          .equals(eventSourceConfig.get(CamundaEventSourceConfigDto.Fields.processDefinitionKey)))
        .findFirst()
        .get();
    } else {
      throw new OptimizeIntegrationTestException("Could not find event source before upgrade");
    }
  }

  private EsEventProcessPublishStateDtoV3Old getPublishStateBeforeUpgrade(
    final List<EsEventProcessPublishStateDtoV3Old> oldPublishStates,
    final String publishStateId) {
    final List<EsEventProcessPublishStateDtoV3Old> oldPublishStatesWithId = oldPublishStates.stream()
      .filter(publishState -> publishState.getId().equals(publishStateId))
      .collect(Collectors.toList());
    assertThat(oldPublishStatesWithId).hasSize(1);
    return oldPublishStatesWithId.get(0);
  }

}
