/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.handlers.ExportHandler.IdAndIndex;
import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.index.TargetIndexLocator;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NotOrdinalControlledIndexExportHandlerTest {
  private final TestHandler handler = new TestHandler();

  @ParameterizedTest
  @ValueSource(
      ints = {StorageOrdinalRelated.NOT_ORDINAL_CONTROLLED, StorageOrdinalRelated.MAIN_INDEX})
  void shouldReturnMainIndexWhenExtractingIdAndIndexes(final int storageOrdinal) {
    // given
    final var indexLocator = new TargetIndexLocator();

    final var record =
        ImmutableRecord.<OrdinalRecordValue>builder()
            .withValue(new OrdinalRecordValue(storageOrdinal))
            .build();

    // when
    final var idAndIndexes = handler.extractIdAndIndexes(indexLocator, record);

    // then
    assertThat(idAndIndexes)
        .isEqualTo(List.of(new IdAndIndex("123", TargetIndex.mainIndex("test-index_"))));
  }

  @ParameterizedTest
  @ValueSource(ints = {-2, 1, 2})
  void shouldNotAllowOrdinalIndexesWhenExtractingIdAndIndexes(final int storageOrdinal) {
    // given
    final var indexLocator = new TargetIndexLocator();

    final var record =
        ImmutableRecord.<OrdinalRecordValue>builder()
            .withValue(new OrdinalRecordValue(storageOrdinal))
            .build();

    // when
    final var thrown = assertThatThrownBy(() -> handler.extractIdAndIndexes(indexLocator, record));

    // then
    thrown
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("should not be ordinal controlled")
        .hasMessageContaining("Storage ordinal was: " + storageOrdinal);
  }

  record OrdinalRecordValue(int storageOrdinal) implements RecordValue, StorageOrdinalRelated {
    @Override
    public int getStorageOrdinal() {
      return storageOrdinal;
    }
  }

  static class TestHandler
      implements NotOrdinalControlledIndexExportHandler<
          ProcessInstanceForListViewEntity, OrdinalRecordValue> {

    @Override
    public ValueType getHandledValueType() {
      return null;
    }

    @Override
    public Class<ProcessInstanceForListViewEntity> getEntityType() {
      return null;
    }

    @Override
    public boolean handlesRecord(final Record<OrdinalRecordValue> record) {
      return false;
    }

    @Override
    public List<String> generateIds(final Record<OrdinalRecordValue> record) {
      return List.of("123");
    }

    @Override
    public ProcessInstanceForListViewEntity createNewEntity(final String id) {
      return new ProcessInstanceForListViewEntity();
    }

    @Override
    public void updateEntity(
        final Record<OrdinalRecordValue> record, final ProcessInstanceForListViewEntity entity) {}

    @Override
    public void flush(
        final TargetIndex index,
        final ProcessInstanceForListViewEntity entity,
        final BatchRequest batchRequest)
        throws PersistenceException {}

    @Override
    public String getIndexName() {
      return "test-index_";
    }
  }
}
