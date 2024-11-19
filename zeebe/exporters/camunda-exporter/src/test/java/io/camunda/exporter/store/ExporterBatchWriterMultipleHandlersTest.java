/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.store;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.store.ExporterBatchWriter.Builder;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Integration test with actual exporter handlers */
public class ExporterBatchWriterMultipleHandlersTest {

  private final ProtocolFactory factory = new ProtocolFactory();

  @Test
  public void shouldCacheEntitiesForMultipleHandlers() {
    // given
    final var writer =
        Builder.begin()
            .withHandler(new TestExportHandler("indexA"))
            .withHandler(new TestExportHandler("indexB"))
            .build();

    // when
    writer.addRecord(
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE,
            builder -> builder.withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)));

    // then
    assertThat(writer.getBatchSize()).isEqualTo(2);
  }

  @Test
  public void shouldFlushIntoSeparateIndicesForMultipleHandlers() throws PersistenceException {
    // given
    final var writer =
        Builder.begin()
            .withHandler(new TestExportHandler("indexA"))
            .withHandler(new TestExportHandler("indexB"))
            .build();

    final var record =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE,
            builder -> builder.withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED));
    writer.addRecord(record);

    final BatchRequest batchRequest = mock(BatchRequest.class);

    // when
    writer.flush(batchRequest);

    // then
    verify(batchRequest)
        .update(eq("indexA"), eq(Long.toString(record.getKey())), any(TestEntity.class));
    verify(batchRequest)
        .update(eq("indexB"), eq(Long.toString(record.getKey())), any(TestEntity.class));
    verify(batchRequest).execute();
  }

  private class TestExportHandler implements ExportHandler<TestEntity, ProcessInstanceRecordValue> {

    private final String indexName;

    private TestExportHandler(final String indexName) {
      this.indexName = indexName;
    }

    @Override
    public ValueType getHandledValueType() {
      return ValueType.PROCESS_INSTANCE;
    }

    @Override
    public Class<TestEntity> getEntityType() {
      return TestEntity.class;
    }

    @Override
    public boolean handlesRecord(final Record record) {
      return true;
    }

    @Override
    public List<String> generateIds(final Record record) {
      return List.of(Long.toString(record.getKey()));
    }

    @Override
    public TestEntity createNewEntity(final String id) {
      return new TestEntity(id);
    }

    @Override
    public void updateEntity(final Record record, final TestEntity entity) {
      entity.setId(Long.toString(record.getKey())).setIntent(record.getIntent());
    }

    @Override
    public void flush(final TestEntity entity, final BatchRequest batchRequest)
        throws PersistenceException {
      batchRequest.update(indexName, entity.getId(), entity);
    }

    @Override
    public String getIndexName() {
      return null;
    }
  }

  private class TestEntity implements ExporterEntity<TestEntity> {

    private String id;
    private Intent intent;

    private TestEntity(final String id) {
      this.id = id;
    }

    public Intent getIntent() {
      return intent;
    }

    public void setIntent(final Intent intent) {
      this.intent = intent;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public TestEntity setId(final String id) {
      this.id = id;
      return this;
    }
  }
}
