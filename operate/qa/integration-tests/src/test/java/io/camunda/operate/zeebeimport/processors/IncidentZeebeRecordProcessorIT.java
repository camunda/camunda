/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.processors;

import static io.camunda.operate.util.TestUtil.createIncident;
import static io.camunda.operate.util.ZeebeRecordTestUtil.createZeebeRecordFromIncident;
import static io.camunda.zeebe.protocol.record.intent.IncidentIntent.MIGRATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.operate.zeebeimport.ImportPositionHolder;
import io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate;
import io.camunda.webapps.schema.entities.operate.IncidentEntity;
import io.camunda.webapps.schema.entities.operate.IncidentState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class IncidentZeebeRecordProcessorIT extends OperateSearchAbstractIT {

  private final String newBpmnProcessId = "newBpmnProcessId";
  private final long newProcessDefinitionKey = 111;
  private final String newFlowNodeId = "newFlowNodeId";
  @Autowired private IncidentTemplate incidentTemplate;
  @Autowired private IncidentZeebeRecordProcessor incidentZeebeRecordProcessor;
  @Autowired private BeanFactory beanFactory;
  @MockBean private PartitionHolder partitionHolder;
  @Autowired private ImportPositionHolder importPositionHolder;

  @Override
  protected void runAdditionalBeforeAllSetup() throws Exception {
    when(partitionHolder.getPartitionIds()).thenReturn(List.of(1));
  }

  @Test
  public void shouldOverrideIncidentFields() throws IOException, PersistenceException {
    // having
    // incident entity with position = 1
    final IncidentEntity inc = createIncident(IncidentState.ACTIVE).setPosition(1L);
    testSearchRepository.createOrUpdateDocumentFromObject(
        incidentTemplate.getFullQualifiedName(), inc.getId(), inc);

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 2L;
    final Record<IncidentRecordValue> zeebeRecord =
        createZeebeRecordFromIncident(
            inc,
            b -> b.withPosition(newPosition).withIntent(MIGRATED),
            b ->
                b.withBpmnProcessId(newBpmnProcessId)
                    .withProcessDefinitionKey(newProcessDefinitionKey)
                    .withElementId(newFlowNodeId));
    importIncidentZeebeRecord(zeebeRecord);

    // then
    // process instance fields are updated
    final IncidentEntity updatedInc = findIncidentByKey(inc.getKey());
    // old values
    assertThat(updatedInc.getTenantId()).isEqualTo(inc.getTenantId());
    assertThat(updatedInc.getKey()).isEqualTo(inc.getKey());
    // new values
    assertThat(updatedInc.getBpmnProcessId()).isEqualTo(newBpmnProcessId);
    assertThat(updatedInc.getProcessDefinitionKey()).isEqualTo(newProcessDefinitionKey);
    assertThat(updatedInc.getFlowNodeId()).isEqualTo(newFlowNodeId);
    assertThat(updatedInc.getPosition()).isEqualTo(newPosition);
  }

  @Test
  public void shouldOverrideIncidentFieldsForNullPosition()
      throws IOException, PersistenceException {
    // having
    // incident entity with position = 1
    final IncidentEntity inc = createIncident(IncidentState.ACTIVE); // null positions field
    testSearchRepository.createOrUpdateDocumentFromObject(
        incidentTemplate.getFullQualifiedName(), inc.getId(), inc);

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 2L;
    final Record<IncidentRecordValue> zeebeRecord =
        createZeebeRecordFromIncident(
            inc,
            b -> b.withPosition(newPosition).withIntent(MIGRATED),
            b ->
                b.withBpmnProcessId(newBpmnProcessId)
                    .withProcessDefinitionKey(newProcessDefinitionKey)
                    .withElementId(newFlowNodeId));
    importIncidentZeebeRecord(zeebeRecord);

    // then
    // process instance fields are updated
    final IncidentEntity updatedInc = findIncidentByKey(inc.getKey());
    // old values
    assertThat(updatedInc.getTenantId()).isEqualTo(inc.getTenantId());
    assertThat(updatedInc.getKey()).isEqualTo(inc.getKey());
    // new values
    assertThat(updatedInc.getBpmnProcessId()).isEqualTo(newBpmnProcessId);
    assertThat(updatedInc.getProcessDefinitionKey()).isEqualTo(newProcessDefinitionKey);
    assertThat(updatedInc.getFlowNodeId()).isEqualTo(newFlowNodeId);
    assertThat(updatedInc.getPosition()).isEqualTo(newPosition);
  }

  @Test
  public void shouldNotOverrideIncidentFields() throws IOException, PersistenceException {
    // having
    // incident entity with position = 2
    final long oldPosition = 2L;
    final IncidentEntity inc = createIncident(IncidentState.ACTIVE).setPosition(oldPosition);
    testSearchRepository.createOrUpdateDocumentFromObject(
        incidentTemplate.getFullQualifiedName(), inc.getId(), inc);

    // when
    // importing Zeebe record with smaller position
    final long newPosition = 1L;
    final Record<IncidentRecordValue> zeebeRecord =
        createZeebeRecordFromIncident(
            inc,
            b -> b.withPosition(newPosition).withIntent(MIGRATED),
            b ->
                b.withBpmnProcessId(newBpmnProcessId)
                    .withProcessDefinitionKey(newProcessDefinitionKey)
                    .withElementId(newFlowNodeId));
    importIncidentZeebeRecord(zeebeRecord);

    // then
    // process instance fields are updated
    final IncidentEntity updatedInc = findIncidentByKey(inc.getKey());
    // old values
    assertThat(updatedInc.getTenantId()).isEqualTo(inc.getTenantId());
    assertThat(updatedInc.getKey()).isEqualTo(inc.getKey());
    // old values
    assertThat(updatedInc.getBpmnProcessId()).isEqualTo(inc.getBpmnProcessId());
    assertThat(updatedInc.getProcessDefinitionKey()).isEqualTo(inc.getProcessDefinitionKey());
    assertThat(updatedInc.getFlowNodeId()).isEqualTo(inc.getFlowNodeId());
    assertThat(updatedInc.getPosition()).isEqualTo(oldPosition);
  }

  @NotNull
  private IncidentEntity findIncidentByKey(final long key) throws IOException {
    final List<IncidentEntity> entities =
        testSearchRepository.searchTerm(
            incidentTemplate.getFullQualifiedName(), "key", key, IncidentEntity.class, 10);
    final Optional<IncidentEntity> first = entities.stream().findFirst();
    assertThat(first.isPresent()).isTrue();
    return first.get();
  }

  private void importIncidentZeebeRecord(final Record<IncidentRecordValue> zeebeRecord)
      throws PersistenceException {
    final BatchRequest batchRequest = beanFactory.getBean(BatchRequest.class);
    incidentZeebeRecordProcessor.processIncidentRecord(List.of(zeebeRecord), batchRequest, true);
    batchRequest.execute();
    searchContainerManager.refreshIndices(incidentTemplate.getFullQualifiedName());
  }
}
