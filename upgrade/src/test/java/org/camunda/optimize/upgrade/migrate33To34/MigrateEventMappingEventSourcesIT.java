/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate33To34;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.query.event.process.IndexableEventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceConfigDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceConfigDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceType;
import org.camunda.optimize.dto.optimize.query.event.process.source.ExternalEventSourceConfigDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.es.schema.index.events.EventProcessMappingIndex;
import org.camunda.optimize.upgrade.migrate33To34.dto.EventSourceEntryDtoOld;
import org.camunda.optimize.upgrade.migrate33To34.dto.IndexableEventProcessMappingDtoV3Old;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.factories.Upgrade33To34PlanFactory;
import org.camunda.optimize.util.SuppressionConstants;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.es.schema.index.events.EventProcessMappingIndex.EVENT_SOURCES;
import static org.camunda.optimize.service.es.schema.index.events.EventProcessMappingIndex.EVENT_SOURCE_CONFIG;
import static org.camunda.optimize.service.es.schema.index.events.EventProcessMappingIndex.EVENT_SOURCE_ID;
import static org.camunda.optimize.service.es.schema.index.events.EventProcessMappingIndex.EVENT_SOURCE_TYPE;

public class MigrateEventMappingEventSourcesIT extends AbstractUpgrade33IT {

  @SneakyThrows
  @Test
  public void eventMappingEventSourcesAreMigratedToNewDataStructure() {
    // given
    executeBulk("steps/3.3/eventmappings/33-event-process-mappings-with-event-sources.json");
    final UpgradePlan upgradePlan = new Upgrade33To34PlanFactory().createUpgradePlan();

    // then
    final List<IndexableEventProcessMappingDtoV3Old> eventMappingsBeforeUpgrade = getAllDocumentsOfIndexAs(
      EVENT_MAPPING_INDEX.getIndexName(),
      IndexableEventProcessMappingDtoV3Old.class
    );

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    final SearchHit[] mappingHitsAfterUpgrade = getAllDocumentsOfIndex(new EventProcessMappingIndex().getIndexName());
    assertThat(mappingHitsAfterUpgrade).hasSize(3)
      .allSatisfy(eventMapping -> {
        final IndexableEventProcessMappingDtoV3Old mappingBeforeUpgrade = getMappingBeforeUpgrade(
          eventMappingsBeforeUpgrade, eventMapping.getId());
        assertEventSourcesHaveBeenUpgraded(mappingBeforeUpgrade, eventMapping);
        assertOtherMappingsPropertiesAreUnaffected(mappingBeforeUpgrade);
      });
  }

  private void assertOtherMappingsPropertiesAreUnaffected(final IndexableEventProcessMappingDtoV3Old mappingBeforeUpgrade) {
    final IndexableEventProcessMappingDto mappingAfterUpgrade = getDocumentOfIndexByIdAs(
      new EventProcessMappingIndex().getIndexName(),
      mappingBeforeUpgrade.getId(),
      IndexableEventProcessMappingDto.class
    ).orElseThrow(() -> new OptimizeIntegrationTestException(
      "Cannot fetch event process mapping with ID: " + mappingBeforeUpgrade.getId()));
    assertThat(mappingBeforeUpgrade.getLastModified()).isEqualTo(mappingAfterUpgrade.getLastModified());
    assertThat(mappingBeforeUpgrade.getLastModifier()).isEqualTo(mappingAfterUpgrade.getLastModifier());
    assertThat(mappingBeforeUpgrade.getName()).isEqualTo(mappingAfterUpgrade.getName());
    assertThat(mappingBeforeUpgrade.getXml()).isEqualTo(mappingAfterUpgrade.getXml());
    assertThat(mappingBeforeUpgrade.getMappings())
      .containsExactlyInAnyOrderElementsOf(mappingAfterUpgrade.getMappings());
    assertThat(mappingBeforeUpgrade.getRoles()).isEqualTo(mappingAfterUpgrade.getRoles());
  }

  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  private void assertEventSourcesHaveBeenUpgraded(final IndexableEventProcessMappingDtoV3Old eventMappingBeforeUpgrade,
                                                  final SearchHit eventMapping) {
    final List<Map<String, Object>> eventSources =
      (List<Map<String, Object>>) eventMapping.getSourceAsMap().get(EVENT_SOURCES);
    eventSources.forEach(eventSource -> {
      final EventSourceEntryDtoOld sourceBeforeUpgrade = getEventSourceFromMapping(
        eventMappingBeforeUpgrade, (String) eventSource.get(EVENT_SOURCE_ID));
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

  private IndexableEventProcessMappingDtoV3Old getMappingBeforeUpgrade(final List<IndexableEventProcessMappingDtoV3Old> oldMappings,
                                                                       final String eventMappingId) {
    final List<IndexableEventProcessMappingDtoV3Old> oldMappingsWithId = oldMappings.stream()
      .filter(mapping -> mapping.getId().equals(eventMappingId))
      .collect(Collectors.toList());
    assertThat(oldMappingsWithId).hasSize(1);
    return oldMappingsWithId.get(0);
  }

  private EventSourceEntryDtoOld getEventSourceFromMapping(final IndexableEventProcessMappingDtoV3Old oldMapping,
                                                           final String sourceId) {
    final List<EventSourceEntryDtoOld> oldSourceWithId = oldMapping.getEventSources().stream()
      .filter(source -> source.getId().equals(sourceId))
      .collect(Collectors.toList());
    assertThat(oldSourceWithId).hasSize(1);
    return oldSourceWithId.get(0);
  }

}
