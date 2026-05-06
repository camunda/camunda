/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.DocumentReferenceEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

public class DocumentReferenceHandlerTest {

  private static final String SINGLE_DOC_REF =
      """
      {
        "camunda.document.type": "camunda",
        "documentId": "doc123",
        "storeId": "aws",
        "contentHash": "hash1",
        "metadata": {
          "fileName": "test.pdf",
          "contentType": "application/pdf",
          "size": 1024,
          "expiresAt": "2025-12-31T00:00:00Z",
          "customProperties": {}
        }
      }
      """;

  private static final String ARRAY_DOC_REFS =
      """
      [
        {
          "camunda.document.type": "camunda",
          "documentId": "doc123",
          "storeId": "aws",
          "contentHash": "hash1",
          "metadata": {
            "fileName": "test.pdf",
            "contentType": "application/pdf",
            "size": 1024,
            "expiresAt": "2025-12-31T00:00:00Z",
            "customProperties": {}
          }
        },
        {
          "camunda.document.type": "camunda",
          "documentId": "doc456",
          "storeId": "gcp",
          "contentHash": "hash2",
          "metadata": {
            "fileName": "report.docx",
            "contentType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "size": 2048,
            "expiresAt": "2025-12-31T00:00:00Z",
            "customProperties": {}
          }
        }
      ]
      """;

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "document-reference";
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final DocumentReferenceHandler underTest =
      new DocumentReferenceHandler(indexName, objectMapper);

  @Test
  void shouldGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.VARIABLE);
  }

  @Test
  void shouldGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(DocumentReferenceEntity.class);
  }

  @Test
  void shouldNotHandleRecordWithMigratedIntent() {
    // given
    final Record<VariableRecordValue> record =
        factory.generateRecord(ValueType.VARIABLE, r -> r.withIntent(VariableIntent.MIGRATED));

    // when - then
    assertThat(underTest.handlesRecord(record)).isFalse();
  }

  @Test
  void shouldNotHandleRecordWithNonDocumentReferenceValue() {
    // given
    final VariableRecordValue recordValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withValue("{\"some\": \"value\"}")
            .build();
    final Record<VariableRecordValue> record =
        factory.generateRecord(
            ValueType.VARIABLE, r -> r.withIntent(VariableIntent.CREATED).withValue(recordValue));

    // when - then
    assertThat(underTest.handlesRecord(record)).isFalse();
  }

  @ParameterizedTest
  @EnumSource(
      value = VariableIntent.class,
      names = {"CREATED", "UPDATED"},
      mode = Mode.INCLUDE)
  void shouldHandleRecordWithSingleDocRef(final VariableIntent intent) {
    // given
    final VariableRecordValue recordValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withValue(SINGLE_DOC_REF)
            .build();
    final Record<VariableRecordValue> record =
        factory.generateRecord(
            ValueType.VARIABLE, r -> r.withIntent(intent).withValue(recordValue));

    // when - then
    assertThat(underTest.handlesRecord(record)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = VariableIntent.class,
      names = {"CREATED", "UPDATED"},
      mode = Mode.INCLUDE)
  void shouldHandleRecordWithArrayOfDocRefs(final VariableIntent intent) {
    // given
    final VariableRecordValue recordValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withValue(ARRAY_DOC_REFS)
            .build();
    final Record<VariableRecordValue> record =
        factory.generateRecord(
            ValueType.VARIABLE, r -> r.withIntent(intent).withValue(recordValue));

    // when - then
    assertThat(underTest.handlesRecord(record)).isTrue();
  }

  @Test
  void shouldGenerateIdsForSingleDocRef() {
    // given
    final VariableRecordValue recordValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withValue(SINGLE_DOC_REF)
            .build();
    final Record<VariableRecordValue> record =
        factory.generateRecord(
            ValueType.VARIABLE, r -> r.withIntent(VariableIntent.CREATED).withValue(recordValue));

    // when
    final var ids = underTest.generateIds(record);

    // then
    assertThat(ids).hasSize(1);
    assertThat(ids.get(0)).isEqualTo(record.getKey() + "_doc123");
  }

  @Test
  void shouldGenerateIdsForArrayOfDocRefs() {
    // given
    final VariableRecordValue recordValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withValue(ARRAY_DOC_REFS)
            .build();
    final Record<VariableRecordValue> record =
        factory.generateRecord(
            ValueType.VARIABLE, r -> r.withIntent(VariableIntent.CREATED).withValue(recordValue));

    // when
    final var ids = underTest.generateIds(record);

    // then
    assertThat(ids).hasSize(2);
    assertThat(ids)
        .containsExactlyInAnyOrder(record.getKey() + "_doc123", record.getKey() + "_doc456");
  }

  @Test
  void shouldCreateNewEntity() {
    // when
    final var result = underTest.createNewEntity("testId");

    // then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("testId");
  }

  @Test
  void shouldAddEntityOnFlush() {
    // given
    final DocumentReferenceEntity entity =
        new DocumentReferenceEntity().setId("id").setDocumentId("doc123");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(entity, mockRequest);

    // then
    verify(mockRequest, times(1)).add(indexName, entity);
  }

  @Test
  void shouldUpdateEntityFromSingleDocRef() {
    // given
    final VariableRecordValue recordValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withValue(SINGLE_DOC_REF)
            .withName("myDoc")
            .build();
    final Record<VariableRecordValue> record =
        factory.generateRecord(
            ValueType.VARIABLE, r -> r.withIntent(VariableIntent.CREATED).withValue(recordValue));

    final DocumentReferenceEntity entity = new DocumentReferenceEntity();
    entity.setId(record.getKey() + "_doc123");

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getVariableKey()).isEqualTo(record.getKey());
    assertThat(entity.getVariableName()).isEqualTo("myDoc");
    assertThat(entity.getDocumentId()).isEqualTo("doc123");
    assertThat(entity.getStoreId()).isEqualTo("aws");
    assertThat(entity.getContentHash()).isEqualTo("hash1");
    assertThat(entity.getFileName()).isEqualTo("test.pdf");
    assertThat(entity.getContentType()).isEqualTo("application/pdf");
    assertThat(entity.getSize()).isEqualTo(1024L);
    assertThat(entity.getExpiresAt()).isEqualTo("2025-12-31T00:00:00Z");
  }

  @Test
  void shouldUpdateEntityFromArrayOfDocRefsPickingCorrectOne() {
    // given
    final VariableRecordValue recordValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withValue(ARRAY_DOC_REFS)
            .withName("myDocs")
            .build();
    final Record<VariableRecordValue> record =
        factory.generateRecord(
            ValueType.VARIABLE, r -> r.withIntent(VariableIntent.CREATED).withValue(recordValue));

    final DocumentReferenceEntity entity = new DocumentReferenceEntity();
    entity.setId(record.getKey() + "_doc456");

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getDocumentId()).isEqualTo("doc456");
    assertThat(entity.getStoreId()).isEqualTo("gcp");
    assertThat(entity.getContentHash()).isEqualTo("hash2");
    assertThat(entity.getFileName()).isEqualTo("report.docx");
    assertThat(entity.getContentType())
        .isEqualTo(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    assertThat(entity.getSize()).isEqualTo(2048L);
    assertThat(entity.getExpiresAt()).isEqualTo("2025-12-31T00:00:00Z");
  }

  @Test
  void shouldNotHandleRecordWhenDiscriminatorAppearsOnlyAsValue() {
    // given - discriminator string appears in a value, not as a key
    final VariableRecordValue recordValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withValue("{\"description\": \"we use camunda.document.type for foo\"}")
            .build();
    final Record<VariableRecordValue> record =
        factory.generateRecord(
            ValueType.VARIABLE,
            r -> r.withIntent(VariableIntent.CREATED).withValue(recordValue));

    // when - then
    assertThat(underTest.handlesRecord(record)).isFalse();
  }

  @Test
  void shouldNotHandleRecordWhenJsonIsMalformedButPassesFastPath() {
    // given - contains the discriminator substring but isn't valid JSON
    final VariableRecordValue recordValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withValue("{\"camunda.document.type\": \"camunda\"")
            .build();
    final Record<VariableRecordValue> record =
        factory.generateRecord(
            ValueType.VARIABLE,
            r -> r.withIntent(VariableIntent.CREATED).withValue(recordValue));

    // when - then
    assertThat(underTest.handlesRecord(record)).isFalse();
  }

  @Test
  void shouldFilterOutNonDocRefElementsFromMixedArray() {
    // given - one element is a doc-ref, the other is a plain object
    final String mixedArray =
        """
        [
          {
            "camunda.document.type": "camunda",
            "documentId": "doc123",
            "storeId": "aws",
            "metadata": {"fileName": "a.pdf"}
          },
          {
            "foo": "bar"
          }
        ]
        """;
    final VariableRecordValue recordValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withValue(mixedArray)
            .build();
    final Record<VariableRecordValue> record =
        factory.generateRecord(
            ValueType.VARIABLE,
            r -> r.withIntent(VariableIntent.CREATED).withValue(recordValue));

    // when
    final boolean handles = underTest.handlesRecord(record);
    final var ids = underTest.generateIds(record);

    // then - only the doc-ref element produces an id
    assertThat(handles).isTrue();
    assertThat(ids).containsExactly(record.getKey() + "_doc123");
  }

  @Test
  void shouldSkipArrayElementWithoutDocumentId() {
    // given - array with one valid doc-ref and one missing documentId
    final String arrayMissingId =
        """
        [
          {
            "camunda.document.type": "camunda",
            "documentId": "doc123",
            "metadata": {}
          },
          {
            "camunda.document.type": "camunda",
            "metadata": {}
          }
        ]
        """;
    final VariableRecordValue recordValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withValue(arrayMissingId)
            .build();
    final Record<VariableRecordValue> record =
        factory.generateRecord(
            ValueType.VARIABLE,
            r -> r.withIntent(VariableIntent.CREATED).withValue(recordValue));

    // when
    final var ids = underTest.generateIds(record);

    // then - only the element with documentId yields an id
    assertThat(ids).containsExactly(record.getKey() + "_doc123");
  }

  @Test
  void shouldSetRootProcessInstanceKeyWhenPositive() {
    // given
    final VariableRecordValue recordValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withValue(SINGLE_DOC_REF)
            .withRootProcessInstanceKey(42L)
            .build();
    final Record<VariableRecordValue> record =
        factory.generateRecord(
            ValueType.VARIABLE,
            r -> r.withIntent(VariableIntent.CREATED).withValue(recordValue));
    final DocumentReferenceEntity entity = new DocumentReferenceEntity();
    entity.setId(record.getKey() + "_doc123");

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getRootProcessInstanceKey()).isEqualTo(42L);
  }

  @Test
  void shouldFallBackToDefaultTenantWhenTenantIsEmpty() {
    // given - empty tenantId triggers tenantOrDefault fallback
    final VariableRecordValue recordValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withValue(SINGLE_DOC_REF)
            .withTenantId("")
            .build();
    final Record<VariableRecordValue> record =
        factory.generateRecord(
            ValueType.VARIABLE,
            r -> r.withIntent(VariableIntent.CREATED).withValue(recordValue));
    final DocumentReferenceEntity entity = new DocumentReferenceEntity();
    entity.setId(record.getKey() + "_doc123");

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getTenantId()).isEqualTo("<default>");
  }

  @Test
  void shouldRunFullLifecycleReusingTheCacheForArrayDocRefs() {
    // given - exercise the cache happy-path: handlesRecord populates, generateIds + updateEntity
    // both consume without re-parsing
    final VariableRecordValue recordValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withValue(ARRAY_DOC_REFS)
            .build();
    final Record<VariableRecordValue> record =
        factory.generateRecord(
            ValueType.VARIABLE,
            r -> r.withIntent(VariableIntent.CREATED).withValue(recordValue));

    // when
    final boolean handles = underTest.handlesRecord(record);
    final var ids = underTest.generateIds(record);
    final DocumentReferenceEntity first = new DocumentReferenceEntity();
    first.setId(ids.get(0));
    underTest.updateEntity(record, first);
    final DocumentReferenceEntity second = new DocumentReferenceEntity();
    second.setId(ids.get(1));
    underTest.updateEntity(record, second);

    // then
    assertThat(handles).isTrue();
    assertThat(ids).hasSize(2);
    assertThat(first.getDocumentId()).isNotEqualTo(second.getDocumentId());
    assertThat(first.getDocumentId()).isIn("doc123", "doc456");
    assertThat(second.getDocumentId()).isIn("doc123", "doc456");
  }
}
