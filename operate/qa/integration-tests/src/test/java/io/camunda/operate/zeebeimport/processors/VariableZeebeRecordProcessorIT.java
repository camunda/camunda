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

import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.operate.zeebeimport.ImportPositionHolder;
import io.camunda.operate.zeebeimport.v8_7.processors.processors.VariableZeebeRecordProcessor;
import io.camunda.webapps.schema.descriptors.operate.template.VariableTemplate;
import io.camunda.webapps.schema.entities.operate.VariableEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
  public void shouldOverrideVariableFields() throws IOException, PersistenceException {
    // having
    // variable entity with position = 1
    final VariableEntity var = createVariable(111L, 222L, "varName", "varValue").setPosition(1L);
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
  public void shouldOverrideVariableFieldsForNullPosition()
      throws IOException, PersistenceException {
    // having
    // variable entity with empty position
    final VariableEntity var = createVariable(111L, 222L, "varName", "varValue"); // null position
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
  public void shouldNotOverrideVariableFields() throws IOException, PersistenceException {
    // having
    // variable entity with position = 2L
    final long oldPosition = 2L;
    final VariableEntity var =
        createVariable(111L, 222L, "varName", "varValue").setPosition(oldPosition);
    testSearchRepository.createOrUpdateDocumentFromObject(
        variableTemplate.getFullQualifiedName(), var.getId(), var);

    // when
    // importing Zeebe record with smaller position
    final long newPosition = 1L;
    final Record<VariableRecordValue> zeebeRecord =
        createZeebeRecordFromVariable(
            var,
            b -> b.withPosition(newPosition).withIntent(VariableIntent.MIGRATED),
            b -> b.withValue(newVarValue));
    importVariableZeebeRecord(zeebeRecord);

    // then
    // process instance fields are updated
    final VariableEntity updatedVar = findVariableById(var.getId());
    // old values
    assertThat(updatedVar.getTenantId()).isEqualTo(var.getTenantId());
    assertThat(updatedVar.getName()).isEqualTo(var.getName());
    // old values
    assertThat(updatedVar.getValue()).isEqualTo(var.getValue());
    assertThat(updatedVar.getFullValue()).isEqualTo(var.getFullValue());
    assertThat(updatedVar.getIsPreview()).isEqualTo(var.getIsPreview());
    assertThat(updatedVar.getPosition()).isEqualTo(oldPosition);
  }

  @Test
  public void shouldNotClearVariableValueDuringMigration()
      throws PersistenceException, IOException {
    // given
    final VariableEntity var = createVariable(111L, 222L, "varName", "varValue");
    testSearchRepository.createOrUpdateDocumentFromObject(
        variableTemplate.getFullQualifiedName(), var.getId(), var);

    // when
    // importing MIGRATED Zeebe record
    final Record<VariableRecordValue> zeebeRecord =
        createZeebeRecordFromVariable(
            var,
            b -> b.withPosition(1L).withIntent(VariableIntent.MIGRATED),
            b -> b.withValue(null));
    importVariableZeebeRecord(zeebeRecord);

    // then
    // the variable value has not been set to null but is still the old value
    final VariableEntity updatedVar = findVariableById(var.getId());
    assertThat(updatedVar.getValue()).isEqualTo(var.getValue());
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
    final BatchRequest batchRequest = beanFactory.getBean(BatchRequest.class);
    variableZeebeRecordProcessor.processVariableRecords(
        Map.of(zeebeRecord.getValue().getScopeKey(), List.of(zeebeRecord)), batchRequest);
    batchRequest.execute();
    searchContainerManager.refreshIndices(variableTemplate.getFullQualifiedName());
  }
}
