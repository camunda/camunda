/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

final class DocumentReferenceTest {

  @Test
  void shouldExposeDefaults() {
    // given
    final var ref = new DocumentReference();

    // then
    assertThat(ref.getDocumentId()).isEmpty();
    assertThat(ref.getStoreId()).isEmpty();
    assertThat(ref.getContentHash()).isEmpty();
    assertThat(ref.getMetadata().getContentType()).isEmpty();
    assertThat(ref.getMetadata().getFileName()).isEmpty();
    assertThat(ref.getMetadata().getExpiresAt()).isEqualTo(-1L);
    assertThat(ref.getMetadata().getSize()).isEqualTo(-1L);
    assertThat(ref.getMetadata().getProcessDefinitionId()).isEmpty();
    assertThat(ref.getMetadata().getProcessInstanceKey()).isEqualTo(-1L);
    assertThat(ref.getMetadata().getCustomProperties()).isEmpty();
  }

  @Test
  void shouldRoundTripScalarFieldsViaMsgPack() {
    // given
    final var original =
        new DocumentReference()
            .setDocumentId("doc-001")
            .setStoreId("gcs-store")
            .setContentHash("sha256-abc");

    // when
    final var copy = new DocumentReference();
    copy.copy(original);

    // then
    assertThat(copy.getDocumentId()).isEqualTo("doc-001");
    assertThat(copy.getStoreId()).isEqualTo("gcs-store");
    assertThat(copy.getContentHash()).isEqualTo("sha256-abc");
  }

  @Test
  void shouldRoundTripAllMetadataFieldsViaMsgPack() {
    // given
    final var original =
        new DocumentReference()
            .setDocumentId("doc-001")
            .setStoreId("gcs-store")
            .setContentHash("sha256-abc");
    original
        .getMetadata()
        .setContentType("application/pdf")
        .setFileName("report.pdf")
        .setExpiresAt(1720000000000L)
        .setSize(4096L)
        .setProcessDefinitionId("order-process")
        .setProcessInstanceKey(2251799813685260L)
        .setCustomProperties(Map.of("region", "eu-west"));

    // when
    final var copy = new DocumentReference();
    copy.copy(original);

    // then
    assertThat(copy.getDocumentId()).isEqualTo("doc-001");
    assertThat(copy.getStoreId()).isEqualTo("gcs-store");
    assertThat(copy.getContentHash()).isEqualTo("sha256-abc");
    final var m = copy.getMetadata();
    assertThat(m.getContentType()).isEqualTo("application/pdf");
    assertThat(m.getFileName()).isEqualTo("report.pdf");
    assertThat(m.getExpiresAt()).isEqualTo(1720000000000L);
    assertThat(m.getSize()).isEqualTo(4096L);
    assertThat(m.getProcessDefinitionId()).isEqualTo("order-process");
    assertThat(m.getProcessInstanceKey()).isEqualTo(2251799813685260L);
    assertThat(m.getCustomProperties()).isEqualTo(Map.of("region", "eu-west"));
  }

  @Test
  void shouldRoundTripCustomPropertiesViaMsgPack() {
    // given
    final Map<String, Object> customProps = Map.of("key1", "value1", "key2", 42, "key3", true);
    final var original = new DocumentReference();
    original.getMetadata().setCustomProperties(customProps);

    // when
    final var copy = new DocumentReference();
    copy.copy(original);

    // then
    assertThat(copy.getMetadata().getCustomProperties()).isEqualTo(customProps);
  }

  @Test
  void shouldCopyFromAnotherDocumentReference() {
    // given
    final var source = new DocumentReference();
    source.setDocumentId("src-doc").setStoreId("src-store").setContentHash("src-hash");
    source.getMetadata().setFileName("source.txt").setSize(512L).setExpiresAt(1700000000000L);

    // when
    final var target = new DocumentReference();
    target.copy(source);

    // then
    assertThat(target.getDocumentId()).isEqualTo("src-doc");
    assertThat(target.getStoreId()).isEqualTo("src-store");
    assertThat(target.getContentHash()).isEqualTo("src-hash");
    assertThat(target.getMetadata().getFileName()).isEqualTo("source.txt");
    assertThat(target.getMetadata().getSize()).isEqualTo(512L);
    assertThat(target.getMetadata().getExpiresAt()).isEqualTo(1700000000000L);
  }
}
