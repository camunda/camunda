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

import io.camunda.exporter.cache.TestFormCache;
import io.camunda.exporter.cache.form.CachedFormEntity;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.form.FormEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.protocol.record.value.deployment.Form;
import io.camunda.zeebe.protocol.record.value.deployment.ImmutableForm;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class FormHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-form";
  private final TestFormCache formCache = new TestFormCache();
  private final FormHandler underTest = new FormHandler(indexName, formCache);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.FORM);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(FormEntity.class);
  }

  @Test
  void shouldHandleRecord() {
    // given
    final Record<Form> formCreatedRecord =
        factory.generateRecord(ValueType.FORM, r -> r.withIntent(FormIntent.CREATED));

    final Record<Form> formDeletedRecord =
        factory.generateRecord(ValueType.FORM, r -> r.withIntent(FormIntent.DELETED));

    // when - then
    assertThat(underTest.handlesRecord(formCreatedRecord)).isTrue();
    assertThat(underTest.handlesRecord(formDeletedRecord)).isTrue();
  }

  @Test
  void shouldGenerateIds() {
    // given
    final long formKey = 123;
    final long recordKey = 456;
    final Record<Form> decisionRecord =
        factory.generateRecord(
            ValueType.FORM,
            r ->
                r.withIntent(FormIntent.CREATED)
                    .withKey(recordKey)
                    .withValue(factory.generateObject(ImmutableForm.class).withFormKey(formKey)));

    // when
    final var idList = underTest.generateIds(decisionRecord);

    // then
    assertThat(idList).containsExactly(String.valueOf(formKey));
  }

  @Test
  void shouldCreateNewEntity() {
    // when
    final var result = underTest.createNewEntity("id");

    // then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("id");
  }

  @Test
  void shouldAddEntityOnFlush() {
    // given
    final FormEntity inputEntity = new FormEntity().setId("111");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).add(indexName, inputEntity);
  }

  @Test
  void shouldAddEntityOnFlushForDeletion() {
    // given
    final FormEntity inputEntity = new FormEntity().setId("111").setKey(123L).setIsDeleted(true);
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).add(indexName, inputEntity);
  }

  @Test
  void shouldUpdateEntityFromRecord() {
    // given
    final long formKey = 123L;
    final ImmutableForm formValue =
        ImmutableForm.builder()
            .from(factory.generateObject(ImmutableForm.class))
            .withResource(formJsonResource().getBytes(StandardCharsets.UTF_8))
            .withFormKey(formKey)
            .build();

    final Record<Form> decisionRecord =
        factory.generateRecord(
            ValueType.FORM, r -> r.withIntent(FormIntent.CREATED).withValue(formValue));

    // when
    final FormEntity formEntity = new FormEntity();
    underTest.updateEntity(decisionRecord, formEntity);

    // then
    assertThat(formEntity.getKey()).isEqualTo(formKey);
    assertThat(formEntity.getVersion()).isEqualTo(formValue.getVersion());
    assertThat(formEntity.getFormId()).isEqualTo(formValue.getFormId());
    assertThat(formEntity.getSchema())
        .isEqualTo(new String(formValue.getResource(), StandardCharsets.UTF_8));
    assertThat(formEntity.getTenantId()).isEqualTo(formValue.getTenantId());
    assertThat(formEntity.getIsDeleted()).isFalse();
    assertThat(formEntity.getEmbedded()).isFalse();
    assertThat(formEntity.getProcessDefinitionId()).isNull();
  }

  @Test
  void shouldUpdateEntityAsDeleted() {
    // given
    final long formKey = 123L;
    final ImmutableForm formValue =
        ImmutableForm.builder()
            .from(factory.generateObject(ImmutableForm.class))
            .withResource(formJsonResource().getBytes(StandardCharsets.UTF_8))
            .withFormKey(formKey)
            .build();

    final Record<Form> decisionRecord =
        factory.generateRecord(
            ValueType.FORM, r -> r.withIntent(FormIntent.DELETED).withValue(formValue));

    // when
    final FormEntity formEntity = new FormEntity();
    underTest.updateEntity(decisionRecord, formEntity);

    // then
    assertThat(formEntity.getKey()).isEqualTo(formKey);
    assertThat(formEntity.getIsDeleted()).isTrue();
    assertThat(formEntity.getVersion()).isEqualTo(formValue.getVersion());
    assertThat(formEntity.getFormId()).isEqualTo(formValue.getFormId());
    assertThat(formEntity.getSchema())
        .isEqualTo(new String(formValue.getResource(), StandardCharsets.UTF_8));
    assertThat(formEntity.getTenantId()).isEqualTo(formValue.getTenantId());
    assertThat(formEntity.getEmbedded()).isFalse();
    assertThat(formEntity.getProcessDefinitionId()).isNull();
  }

  @Test
  void shouldUpdateFormCache() throws IOException {
    // given
    final long formKey = 123L;
    final ImmutableForm formValue =
        ImmutableForm.builder()
            .from(factory.generateObject(ImmutableForm.class))
            .withResource(formJsonResource().getBytes(StandardCharsets.UTF_8))
            .withFormKey(formKey)
            .withFormId("form-id")
            .withVersion(5)
            .build();

    final Record<Form> formRecord =
        factory.generateRecord(
            ValueType.FORM, r -> r.withIntent(FormIntent.CREATED).withValue(formValue));

    // when
    final var formEntity = new FormEntity().setId(String.valueOf(formKey));
    underTest.updateEntity(formRecord, formEntity);

    // then
    assertThat(formCache.get(String.valueOf(formKey)))
        .isPresent()
        .get()
        .extracting(CachedFormEntity::formId, CachedFormEntity::formVersion)
        .containsExactly("form-id", 5L);
  }

  private String formJsonResource() {
    return """
{
  "components": [
    {
      "label": "Payload",
      "type": "textfield",
      "layout": {
        "row": "Row_0bhzxr0",
        "columns": null
      },
      "id": "Field_1lu1cdw",
      "key": "textfield_payload"
    },
    {
      "subtype": "date",
      "dateLabel": "Date",
      "label": "Date time",
      "type": "datetime",
      "layout": {
        "row": "Row_1gqtlv4",
        "columns": null
      },
      "id": "Field_1gb54gn",
      "key": "datetime_ypscpg"
    }
  ],
  "type": "default",
  "id": "startDataForm",
  "executionPlatform": "Camunda Cloud",
  "executionPlatformVersion": "8.5.0",
  "exporter": {
    "name": "Camunda Modeler",
    "version": "5.27.0"
  },
  "schemaVersion": 16
}
""";
  }
}
