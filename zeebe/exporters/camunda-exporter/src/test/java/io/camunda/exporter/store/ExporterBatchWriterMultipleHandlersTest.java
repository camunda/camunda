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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.store.ExporterBatchWriter.Builder;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

/** Integration test with actual exporter handlers */
public class ExporterBatchWriterMultipleHandlersTest {

  private final ProtocolFactory factory = new ProtocolFactory();

  @Test
  public void shouldCacheMultipleHandlersWithSameEntityOnce() {
    // given
    final var writer =
        Builder.begin()
            .withHandler(TestExportHandler.defaultHandler())
            .withHandler(TestExportHandler.defaultHandler())
            .build();

    // when
    writer.addRecord(
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE,
            builder -> builder.withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)));

    // then
    assertThat(writer.getBatchSize()).isEqualTo(1);
  }

  @Test
  public void shouldCacheMultipleDifferentEntitiesForMultipleHandlers() {
    // given
    final var writer =
        Builder.begin()
            .withHandler(
                TestExportHandler.handlerForEntity(OtherTestEntity.class, OtherTestEntity::new))
            .withHandler(TestExportHandler.defaultHandler())
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
  public void shouldFlushMultipleHandlersWithSameEntity() throws PersistenceException {
    // given
    final var writer =
        Builder.begin()
            .withHandler(TestExportHandler.defaultHandler())
            .withHandler(TestExportHandler.defaultHandler())
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
    verify(batchRequest, times(2))
        .update(eq("indexA"), eq(Long.toString(record.getKey())), any(TestEntity.class));
    verify(batchRequest).execute(any());
  }

  @Test
  public void shouldFlushIntoSeparateIndicesForMultipleHandlersWithSameEntity()
      throws PersistenceException {
    // given
    final var writer =
        Builder.begin()
            .withHandler(TestExportHandler.handlerForIndex("indexA"))
            .withHandler(TestExportHandler.handlerForIndex("indexB"))
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
    verify(batchRequest).execute(any());
  }

  @Test
  public void shouldFlushMultiHandlersInOrder() throws PersistenceException {
    // given
    final var writer =
        Builder.begin()
            .withHandler(TestExportHandler.handlerForIndex("indexA"))
            .withHandler(TestExportHandler.handlerForIndex("indexB"))
            .withHandler(TestExportHandler.handlerForIndex("indexC"))
            .withHandler(TestExportHandler.handlerForIndex("indexD"))
            .withHandler(TestExportHandler.handlerForIndex("indexE"))
            .withHandler(TestExportHandler.handlerForIndex("indexF"))
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
    final InOrder inOrder = Mockito.inOrder(batchRequest);
    inOrder
        .verify(batchRequest)
        .update(eq("indexA"), eq(Long.toString(record.getKey())), any(TestEntity.class));
    inOrder
        .verify(batchRequest)
        .update(eq("indexB"), eq(Long.toString(record.getKey())), any(TestEntity.class));
    inOrder
        .verify(batchRequest)
        .update(eq("indexC"), eq(Long.toString(record.getKey())), any(TestEntity.class));
    inOrder
        .verify(batchRequest)
        .update(eq("indexD"), eq(Long.toString(record.getKey())), any(TestEntity.class));
    inOrder
        .verify(batchRequest)
        .update(eq("indexE"), eq(Long.toString(record.getKey())), any(TestEntity.class));
    inOrder
        .verify(batchRequest)
        .update(eq("indexF"), eq(Long.toString(record.getKey())), any(TestEntity.class));
    inOrder.verify(batchRequest).execute(any());
  }

  @Test
  public void shouldFlushMultipleDifferentEntitiesForMultipleHandlers()
      throws PersistenceException {
    // given
    final var writer =
        Builder.begin()
            .withHandler(
                TestExportHandler.handlerForEntity(OtherTestEntity.class, OtherTestEntity::new))
            .withHandler(TestExportHandler.defaultHandler())
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
        .update(eq("indexA"), eq(Long.toString(record.getKey())), any(OtherTestEntity.class));
    verify(batchRequest).execute(any());
  }

  private static class TestEntity implements ExporterEntity<TestEntity> {

    private String id;

    TestEntity(final String id) {
      this.id = id;
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

  private static class OtherTestEntity implements ExporterEntity<OtherTestEntity> {

    private String id;

    OtherTestEntity(final String id) {
      this.id = id;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public OtherTestEntity setId(final String id) {
      this.id = id;
      return this;
    }
  }

  private static class TestExportHandler<T extends ExporterEntity<T>>
      implements ExportHandler<T, ProcessInstanceRecordValue> {

    private final String indexName;
    private final Class<T> clazz;
    private final Function<String, T> constructor;
    private final Predicate<Record> handlesRecord;

    public TestExportHandler(
        final String indexName, final Class<T> clazz, final Function<String, T> constructor) {
      this(indexName, clazz, constructor, ignored -> true);
    }

    public TestExportHandler(
        final String indexName,
        final Class<T> clazz,
        final Function<String, T> constructor,
        final Predicate<Record> handlesRecord) {
      this.indexName = indexName;
      this.clazz = clazz;
      this.constructor = constructor;
      this.handlesRecord = handlesRecord;
    }

    public static TestExportHandler<TestEntity> defaultHandler() {
      return new TestExportHandler<>("indexA", TestEntity.class, TestEntity::new);
    }

    public static <T extends ExporterEntity<T>> TestExportHandler<T> handlerForEntity(
        final Class<T> clazz, final Function<String, T> constructor) {
      return new TestExportHandler<>("indexA", clazz, constructor);
    }

    public static TestExportHandler<TestEntity> handlerForIndex(final String indexName) {
      return new TestExportHandler<>(indexName, TestEntity.class, TestEntity::new);
    }

    public static TestExportHandler<TestEntity> handlerAcceptingOnly(
        final Predicate<Record> handlesRecord) {
      return new TestExportHandler<>("indexA", TestEntity.class, TestEntity::new, handlesRecord);
    }

    @Override
    public ValueType getHandledValueType() {
      return ValueType.PROCESS_INSTANCE;
    }

    @Override
    public Class<T> getEntityType() {
      return clazz;
    }

    @Override
    public boolean handlesRecord(final Record record) {
      return handlesRecord.test(record);
    }

    @Override
    public List<String> generateIds(final Record record) {
      return List.of(Long.toString(record.getKey()));
    }

    @Override
    public T createNewEntity(final String id) {
      return constructor.apply(id);
    }

    @Override
    public void updateEntity(final Record<ProcessInstanceRecordValue> record, final T entity) {
      entity.setId(Long.toString(record.getKey()));
    }

    @Override
    public void flush(final T entity, final BatchRequest batchRequest) throws PersistenceException {
      batchRequest.update(indexName, entity.getId(), entity);
    }

    @Override
    public String getIndexName() {
      return null;
    }
  }

  @Nested
  final class ExportDurationObserverTest {
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ExporterBatchWriter batchWriter =
        ExporterBatchWriter.Builder.begin(
                new CamundaExporterMetrics(
                    meterRegistry, InstantSource.fixed(Instant.ofEpochMilli(20L))))
            .withHandler(
                TestExportHandler.handlerAcceptingOnly(
                    r -> r.getOperationReference() != Long.MAX_VALUE))
            .build();
    private final ProtocolFactory protocolFactory = new ProtocolFactory();
    private final BatchRequest batchRequest = mock(BatchRequest.class);

    @Test
    void shouldObserveDurationOfHandledRecordsOnly() {
      // given
      final var observedRecords =
          List.of(
              protocolFactory.generateRecord(
                  ValueType.PROCESS_INSTANCE, b -> b.withPosition(1L).withTimestamp(4L)),
              protocolFactory.generateRecord(
                  ValueType.PROCESS_INSTANCE, b -> b.withPosition(2L).withTimestamp(8L)));
      final var unobservedRecord =
          protocolFactory.generateRecord(
              ValueType.PROCESS_INSTANCE,
              b -> b.withPosition(10L).withTimestamp(12L).withOperationReference(Long.MAX_VALUE));

      //  when
      observedRecords.forEach(batchWriter::addRecord);
      batchWriter.addRecord(unobservedRecord);
      batchWriter.flush(batchRequest);

      // then
      final var timer = meterRegistry.get("zeebe.camunda.exporter.record.export.duration").timer();
      Assertions.assertThat(timer.count()).isEqualTo(2);
      Assertions.assertThat(timer.max(TimeUnit.MILLISECONDS)).isEqualTo(16L);
      Assertions.assertThat(timer.mean(TimeUnit.MILLISECONDS)).isEqualTo(14L);
    }

    @Test
    void shouldNotObserveSameMetricsOnSuccessiveFlush() {
      // given
      final var record =
          protocolFactory.generateRecord(
              ValueType.PROCESS_INSTANCE, b -> b.withPosition(1L).withTimestamp(4L));

      //  when
      batchWriter.addRecord(record);
      batchWriter.addRecord(record);
      batchWriter.addRecord(record);
      batchWriter.flush(batchRequest);

      // then
      final var timer = meterRegistry.get("zeebe.camunda.exporter.record.export.duration").timer();
      Assertions.assertThat(timer.count()).isOne();
      Assertions.assertThat(timer.max(TimeUnit.MILLISECONDS)).isEqualTo(16L);
      Assertions.assertThat(timer.mean(TimeUnit.MILLISECONDS)).isEqualTo(16L);
    }

    @Test
    void shouldNotObserveSameRecordTwice() {
      // given
      final var record =
          protocolFactory.generateRecord(
              ValueType.PROCESS_INSTANCE, b -> b.withPosition(1L).withTimestamp(4L));

      //  when
      batchWriter.addRecord(record);
      batchWriter.addRecord(record);
      batchWriter.addRecord(record);
      batchWriter.flush(batchRequest);

      // then
      final var timer = meterRegistry.get("zeebe.camunda.exporter.record.export.duration").timer();
      Assertions.assertThat(timer.count()).isOne();
      Assertions.assertThat(timer.max(TimeUnit.MILLISECONDS)).isEqualTo(16L);
      Assertions.assertThat(timer.mean(TimeUnit.MILLISECONDS)).isEqualTo(16L);
    }
  }
}
