/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate33To34;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.query.event.process.IndexableEventProcessPublishStateDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceConfigDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceConfigDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceType;
import org.camunda.optimize.dto.optimize.query.event.process.source.ExternalEventSourceConfigDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.es.schema.index.events.EventProcessPublishStateIndex;
import org.camunda.optimize.upgrade.migrate33To34.dto.EventImportSourceDtoOld;
import org.camunda.optimize.upgrade.migrate33To34.dto.EventSourceEntryDtoOld;
import org.camunda.optimize.upgrade.migrate33To34.dto.IndexableEventProcessPublishStateDtoV3Old;
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
import static org.camunda.optimize.service.es.schema.index.events.EventProcessPublishStateIndex.EVENT_SOURCE;
import static org.camunda.optimize.service.es.schema.index.events.EventProcessPublishStateIndex.EVENT_SOURCE_CONFIG;
import static org.camunda.optimize.service.es.schema.index.events.EventProcessPublishStateIndex.EVENT_SOURCE_TYPE;

public class MigrateEventPublishStateEventSourcesIT extends AbstractUpgrade33IT {

  @SneakyThrows
  @Test
  public void eventProcessPublishStateEventSourcesAreMigratedToNewDataStructure() {
    // given
    executeBulk("steps/3.3/publishstates/33-event-publish-states-with-event-sources.json");
    final UpgradePlan upgradePlan = new Upgrade33To34PlanFactory().createUpgradePlan();

    // then
    final List<IndexableEventProcessPublishStateDtoV3Old> publishStatesBeforeUpgrade = getAllDocumentsOfIndexAs(
      EVENT_PUBLISH_STATE_INDEX.getIndexName(),
      IndexableEventProcessPublishStateDtoV3Old.class
    );

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    final SearchHit[] publishStateHitsAfterUpgrade =
      getAllDocumentsOfIndex(new EventProcessPublishStateIndex().getIndexName());
    assertThat(publishStateHitsAfterUpgrade).hasSize(3)
      .allSatisfy(publishState -> {
        final IndexableEventProcessPublishStateDtoV3Old beforeUpgrade = getPublishStateBeforeUpgrade(
          publishStatesBeforeUpgrade, publishState.getId());
        assertEventSourceHasBeenUpgraded(beforeUpgrade, publishState);
        assertOtherPublishStatePropertiesAreUnaffected(beforeUpgrade);
      });
  }

  private void assertOtherPublishStatePropertiesAreUnaffected(final IndexableEventProcessPublishStateDtoV3Old beforeUpgrade) {
    final IndexableEventProcessPublishStateDto afterUpgrade = getDocumentOfIndexByIdAs(
      new EventProcessPublishStateIndex().getIndexName(),
      beforeUpgrade.getId(),
      IndexableEventProcessPublishStateDto.class
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
  private void assertEventSourceHasBeenUpgraded(final IndexableEventProcessPublishStateDtoV3Old publishStateBeforeUpgrade,
                                                final SearchHit publishState) {
    final List<Map<String, Object>> importSources =
      (List<Map<String, Object>>) publishState.getSourceAsMap().get(EVENT_IMPORT_SOURCES);
    importSources.forEach(importSource -> {
      Map<String, Object> eventSource =
        (Map<String, Object>) importSource.get(EVENT_SOURCE);
      final EventSourceEntryDtoOld sourceBeforeUpgrade = getEventSourceForPublishState(
        publishStateBeforeUpgrade,
        (String) eventSource.get(EventProcessPublishStateIndex.EVENT_SOURCE_ID)
      );
      assertThat(sourceBeforeUpgrade.getType().getId()).isEqualTo(eventSource.get(EVENT_SOURCE_TYPE));
      assertThat(eventSource.get(EVENT_SOURCE_CONFIG)).isNotNull();
      final Map<String, Object> sourceConfig =
        (Map<String, Object>) eventSource.get(EVENT_SOURCE_CONFIG);
      assertThat(sourceBeforeUpgrade.getEventScope().stream().map(EventScopeType::getId).collect(Collectors.toList()))
        .containsExactlyInAnyOrderElementsOf(((List<String>) sourceConfig.get(EventSourceConfigDto.Fields.eventScope)));
      if (EventSourceType.EXTERNAL.getId().equals(eventSource.get(EVENT_SOURCE_TYPE))) {
        assertThat(sourceConfig).containsEntry(ExternalEventSourceConfigDto.Fields.includeAllGroups, true);
        assertThat(sourceConfig.get(ExternalEventSourceConfigDto.Fields.group)).isNull();
      } else if (EventSourceType.CAMUNDA.getId()
        .equals(eventSource.get(EVENT_SOURCE_TYPE))) {
        assertThat(sourceConfig).containsEntry(
          CamundaEventSourceConfigDto.Fields.processDefinitionKey, sourceBeforeUpgrade.getProcessDefinitionKey());
        assertThat(sourceConfig.get(CamundaEventSourceConfigDto.Fields.processDefinitionName)).isNull();
        assertThat(sourceConfig).containsEntry(
          CamundaEventSourceConfigDto.Fields.tracedByBusinessKey, sourceBeforeUpgrade.isTracedByBusinessKey());
        assertThat(sourceConfig).containsEntry(
          CamundaEventSourceConfigDto.Fields.traceVariable, sourceBeforeUpgrade.getTraceVariable());
        assertThat(sourceConfig).containsEntry(
          CamundaEventSourceConfigDto.Fields.tenants, sourceBeforeUpgrade.getTenants());
        assertThat(sourceConfig).containsEntry(
          CamundaEventSourceConfigDto.Fields.versions, sourceBeforeUpgrade.getVersions());
      }
      assertThat(eventSource.get(EventSourceEntryDtoOld.Fields.eventScope)).isNull();
      assertThat(eventSource.get(EventSourceEntryDtoOld.Fields.processDefinitionKey)).isNull();
      assertThat(eventSource.get(EventSourceEntryDtoOld.Fields.tracedByBusinessKey)).isNull();
      assertThat(eventSource.get(EventSourceEntryDtoOld.Fields.traceVariable)).isNull();
      assertThat(eventSource.get(EventSourceEntryDtoOld.Fields.tenants)).isNull();
      assertThat(eventSource.get(EventSourceEntryDtoOld.Fields.versions)).isNull();
    });
  }

  private IndexableEventProcessPublishStateDtoV3Old getPublishStateBeforeUpgrade(
    final List<IndexableEventProcessPublishStateDtoV3Old> oldPublishStates,
    final String publishStateId) {
    final List<IndexableEventProcessPublishStateDtoV3Old> oldPublishStatesWithId = oldPublishStates.stream()
      .filter(publishState -> publishState.getId().equals(publishStateId))
      .collect(Collectors.toList());
    assertThat(oldPublishStatesWithId).hasSize(1);
    return oldPublishStatesWithId.get(0);
  }

  private EventSourceEntryDtoOld getEventSourceForPublishState(final IndexableEventProcessPublishStateDtoV3Old oldPublishState,
                                                               final String sourceId) {
    final List<EventSourceEntryDtoOld> oldSourceWithId = oldPublishState.getEventImportSources().stream()
      .map(EventImportSourceDtoOld::getEventSource)
      .filter(source -> source.getId().equals(sourceId))
      .collect(Collectors.toList());
    assertThat(oldSourceWithId).hasSize(1);
    return oldSourceWithId.get(0);
  }

}
