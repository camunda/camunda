/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.processors;

import static io.camunda.operate.util.TestUtil.createVariable;
import static io.camunda.operate.util.ZeebeRecordTestUtil.createZeebeRecordFromVariable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.operate.entities.VariableEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.VariableTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.operate.zeebeimport.ImportPositionHolder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class VariableZeebeRecordProcessorIT extends OperateSearchAbstractIT {

  @Autowired private VariableTemplate variableTemplate;
  @Autowired private VariableZeebeRecordProcessor variableZeebeRecordProcessor;
  @Autowired private BeanFactory beanFactory;
  @MockBean private PartitionHolder partitionHolder;
  @Autowired private ImportPositionHolder importPositionHolder;
  private final String newVarValue = "newVarValue";

  @Override
  protected void runAdditionalBeforeAllSetup() throws Exception {
    when(partitionHolder.getPartitionIds()).thenReturn(List.of(1));
  }

  @Test
  public void shouldUpdateVariable() throws IOException, PersistenceException {
    // having
    // variable entity with position = 1
    final VariableEntity var =
        createVariable(111L, 456L, "processId", 222L, "varName", "varValue").setPosition(1L);
    testSearchRepository.createOrUpdateDocumentFromObject(
        variableTemplate.getFullQualifiedName(), var.getId(), var);

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 2L;
    final Record<VariableRecordValue> zeebeRecord =
        createZeebeRecordFromVariable(
            var,
            b -> b.withPosition(newPosition).withIntent(VariableIntent.UPDATED),
            b -> b.withValue(newVarValue));
    importVariableZeebeRecord(zeebeRecord);

    // then
    // process instance fields are updated
    final VariableEntity updatedVar = findVariableById(var.getId());
    // old values
    assertThat(updatedVar.getTenantId()).isEqualTo(var.getTenantId());
    assertThat(updatedVar.getName()).isEqualTo(var.getName());
    // new values
    assertThat(updatedVar.getValue()).isEqualTo(newVarValue);
    assertThat(updatedVar.getFullValue()).isEqualTo(null);
    assertThat(updatedVar.getIsPreview()).isFalse();
    assertThat(updatedVar.getPosition()).isEqualTo(newPosition);
  }

  @Test
  public void shouldHandleVariableMigratedEvent() throws PersistenceException, IOException {
    // given
    final VariableEntity originalVariable =
        createVariable(111L, 123L, "someProcessId", 222L, "varName", "varValue");
    testSearchRepository.createOrUpdateDocumentFromObject(
        variableTemplate.getFullQualifiedName(), originalVariable.getId(), originalVariable);

    final long migratedProcessDefinitionKey = 456L;
    final String migratedBpmnProcessId = "anotherProcessId";
    final VariableEntity migratedVariable =
        createVariable(
            111L, migratedProcessDefinitionKey, migratedBpmnProcessId, 222L, "varName", null);

    final Record<VariableRecordValue> migratedZeebeRecord =
        createZeebeRecordFromVariable(
            migratedVariable, b -> b.withPosition(2L).withIntent(VariableIntent.MIGRATED), b -> {});

    // when
    // importing MIGRATED Zeebe record
    importVariableZeebeRecord(migratedZeebeRecord);

    // then
    final VariableEntity updatedVariable = findVariableById(originalVariable.getId());

    // the variable value has not been set to null but is still the old value
    assertThat(updatedVariable.getValue()).isEqualTo(originalVariable.getValue());

    // but the process-related properties have been updated
    assertThat(updatedVariable.getProcessDefinitionKey()).isEqualTo(migratedProcessDefinitionKey);
    assertThat(updatedVariable.getBpmnProcessId()).isEqualTo(migratedBpmnProcessId);
  }

  @Test
  public void shouldHandleVariableCreatedAndMigratedEventInSameBatch()
      throws IOException, PersistenceException {
    // given
    final VariableEntity baseVariable =
        createVariable(111L, 123L, "someProcessId", 222L, "varName", "varValue");

    final long migratedProcessDefinitionKey = 456L;
    final String migratedBpmnProcessId = "anotherProcessId";

    final Record<VariableRecordValue> createdZeebeRecord =
        createZeebeRecordFromVariable(
            baseVariable, b -> b.withPosition(1L).withIntent(VariableIntent.CREATED), b -> {});
    final Record<VariableRecordValue> migratedZeebeRecord =
        createZeebeRecordFromVariable(
            baseVariable,
            b -> b.withPosition(1L).withIntent(VariableIntent.MIGRATED),
            b ->
                b.withValue(null)
                    .withBpmnProcessId(migratedBpmnProcessId)
                    .withProcessDefinitionKey(migratedProcessDefinitionKey));

    // when
    importVariableZeebeRecords(createdZeebeRecord, migratedZeebeRecord);

    // then
    final VariableEntity importedVariable = findVariableById(baseVariable.getId());

    // the variable value has not been set to null but is the value of the CREATED record
    assertThat(importedVariable.getValue()).isEqualTo(baseVariable.getValue());

    // but the process-related properties have been updated to the value of the MIGRATED record
    assertThat(importedVariable.getProcessDefinitionKey()).isEqualTo(migratedProcessDefinitionKey);
    assertThat(importedVariable.getBpmnProcessId()).isEqualTo(migratedBpmnProcessId);
  }

  @NotNull
  private VariableEntity findVariableById(final String id) throws IOException {
    final List<VariableEntity> entities =
        testSearchRepository.searchTerm(
            variableTemplate.getFullQualifiedName(), "_id", id, VariableEntity.class, 10);
    final Optional<VariableEntity> first = entities.stream().findFirst();
    assertThat(first.isPresent()).isTrue();
    return first.get();
  }

  private void importVariableZeebeRecord(final Record<VariableRecordValue> zeebeRecord)
      throws PersistenceException {
    importVariableZeebeRecords(zeebeRecord);
  }

  private void importVariableZeebeRecords(Record<VariableRecordValue>... zeebeRecords)
      throws PersistenceException {
    final Map<Long, List<Record<VariableRecordValue>>> groupedRecords =
        Arrays.stream(zeebeRecords)
            .collect(Collectors.groupingBy(r -> r.getValue().getScopeKey(), Collectors.toList()));

    final BatchRequest batchRequest = beanFactory.getBean(BatchRequest.class);
    variableZeebeRecordProcessor.processVariableRecords(groupedRecords, batchRequest);
    batchRequest.execute();
    searchContainerManager.refreshIndices(variableTemplate.getFullQualifiedName());
  }
}
