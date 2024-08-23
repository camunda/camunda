/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.zeebe.protocol.record.ValueType.NULL_VAL;

import io.camunda.exporter.entities.TestExporterEntity;
import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.protocol.TestValue;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.List;

public class TestExportHandler implements ExportHandler<TestExporterEntity, TestValue> {

  @Override
  public ValueType getHandledValueType() {
    return NULL_VAL;
  }

  @Override
  public Class<TestExporterEntity> getEntityType() {
    return TestExporterEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<TestValue> record) {
    return true;
  }

  @Override
  public List<String> generateIds(final Record<TestValue> record) {
    return List.of();
  }

  @Override
  public TestExporterEntity createNewEntity(final String id) {
    return new TestExporterEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<TestValue> record, final TestExporterEntity entity) {}

  @Override
  public void flush(final TestExporterEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {}
}
