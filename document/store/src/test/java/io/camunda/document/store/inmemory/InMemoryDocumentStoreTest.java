/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store.inmemory;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.document.api.DocumentContent;
import io.camunda.document.api.DocumentCreationRequest;
import io.camunda.document.api.DocumentError;
import io.camunda.document.api.DocumentLink;
import io.camunda.document.api.DocumentMetadataModel;
import io.camunda.document.api.DocumentReference;
import io.camunda.zeebe.util.Either;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class InMemoryDocumentStoreTest {

  @Test
  public void createDocumentKeyExistsShouldFail() {
    // given
    final InMemoryDocumentStore store = new InMemoryDocumentStore();
    final String key = "key";
    final var content = "content".getBytes();

    // when
    final var request =
        new DocumentCreationRequest(
            key,
            new ByteArrayInputStream(content),
            new DocumentMetadataModel(
                "application/json",
                "hello.json",
                OffsetDateTime.now(),
                10L,
                "myProcessDefinition",
                123L,
                Map.of("key", "value")));
    final var result = store.createDocument(request).join();

    assertThat(result).isInstanceOf(Either.Right.class);

    // when
    final var result2 = store.createDocument(request).join();

    // then
    assertThat(result2).isInstanceOf(Either.Left.class);
    assertThat(((Either.Left<DocumentError, DocumentReference>) result2).value())
        .isInstanceOf(DocumentError.DocumentAlreadyExists.class);
  }

  @Test
  public void createDocumentShouldSucceed() {
    // given
    final InMemoryDocumentStore store = new InMemoryDocumentStore();
    final String key = "key";
    final var metadata =
        new DocumentMetadataModel(
            "application/json",
            "hello.json",
            OffsetDateTime.now(),
            10L,
            null,
            null,
            Map.of("key", "value"));

    // when
    final var request =
        new DocumentCreationRequest(key, new ByteArrayInputStream("content".getBytes()), metadata);
    final var result = store.createDocument(request).join();

    // then
    assertThat(result).isInstanceOf(Either.Right.class);
    final var reference = ((Either.Right<DocumentError, DocumentReference>) result).value();
    assertThat(reference).isNotNull();
    assertThat(reference.documentId()).isEqualTo(key);
    assertThat(reference.metadata()).isEqualTo(metadata);
  }

  @Test
  public void deleteDocumentShouldSucceedOnlyIfDocumentExists() {
    // given
    final InMemoryDocumentStore store = new InMemoryDocumentStore();
    final String id = "key";

    // when
    final var request =
        new DocumentCreationRequest(
            id,
            new ByteArrayInputStream("content".getBytes()),
            new DocumentMetadataModel(
                "application/json",
                "hello.json",
                OffsetDateTime.now(),
                10L,
                null,
                null,
                Map.of("key", "value")));
    final var result = store.createDocument(request).join();

    // then
    assertThat(result).isInstanceOf(Either.Right.class);

    // when
    final var result2 = store.deleteDocument(id).join();

    // then
    assertThat(result2).isInstanceOf(Either.Right.class);

    // when
    final var result3 = store.deleteDocument(id).join();

    // then
    assertThat(result3).isInstanceOf(Either.Left.class);
    assertThat(((Either.Left<DocumentError, Void>) result3).value())
        .isInstanceOf(DocumentError.DocumentNotFound.class);
  }

  @Test
  public void getDocumentShouldSucceedOnlyIfDocumentExists() throws IOException {
    // given
    final InMemoryDocumentStore store = new InMemoryDocumentStore();
    final String id = "key";

    // when
    final var request =
        new DocumentCreationRequest(
            id,
            new ByteArrayInputStream("content".getBytes()),
            new DocumentMetadataModel(
                "application/json",
                "hello.json",
                OffsetDateTime.now(),
                10L,
                null,
                null,
                Map.of("key", "value")));

    final var result = store.createDocument(request).join();

    // then
    assertThat(result).isInstanceOf(Either.Right.class);

    // when
    final var result2 = store.getDocument(id).join();

    // then
    assertThat(result2).isInstanceOf(Either.Right.class);
    final var stream =
        ((Either.Right<DocumentError, DocumentContent>) result2).value().inputStream();
    assertThat(stream).isNotNull();
    final var content = new String(stream.readAllBytes());
    assertThat(content).isEqualTo("content");

    // when
    final var result3 = store.getDocument("non-existing").join();

    // then
    assertThat(result3).isInstanceOf(Either.Left.class);
    assertThat(((Either.Left<DocumentError, DocumentContent>) result3).value())
        .isInstanceOf(DocumentError.DocumentNotFound.class);
  }

  @Test
  public void createLinkShouldFail() {
    // given
    final InMemoryDocumentStore store = new InMemoryDocumentStore();
    final String id = "key";

    // when
    final var result = store.createLink(id, 1000L).join();

    // then
    assertThat(result).isInstanceOf(Either.Left.class);
    assertThat(((Either.Left<DocumentError, DocumentLink>) result).value())
        .isInstanceOf(DocumentError.OperationNotSupported.class);
  }

  @ParameterizedTest
  @MethodSource("metadataSingleParamProvider")
  public void shouldHandleMetadataParamsCorrectly(final DocumentMetadataModel metadata) {
    // given
    final InMemoryDocumentStore store = new InMemoryDocumentStore();
    final String id = "key";

    // when
    final var request =
        new DocumentCreationRequest(id, new ByteArrayInputStream("content".getBytes()), metadata);

    final var result = store.createDocument(request).join();

    // then
    assertThat(result).isInstanceOf(Either.Right.class);
    final var reference = ((Either.Right<DocumentError, DocumentReference>) result).value();
    assertThat(reference).isNotNull();
    assertThat(reference.metadata()).isEqualTo(metadata);
  }

  public static Stream<Arguments> metadataSingleParamProvider() {
    // Filename is always set here to prevent it from being overridden with the id in the store to
    // simplify assertions
    return Stream.of(
        Arguments.of(
            new DocumentMetadataModel("text/plain", "fileName", null, null, null, null, null)),
        Arguments.of(new DocumentMetadataModel(null, "fileName", null, null, null, null, null)),
        Arguments.of(
            new DocumentMetadataModel(
                null, "fileName", OffsetDateTime.now(), null, null, null, null)),
        Arguments.of(new DocumentMetadataModel(null, "fileName", null, 1L, null, null, null)),
        Arguments.of(
            new DocumentMetadataModel(null, "fileName", null, null, "definitionId", null, null)),
        Arguments.of(new DocumentMetadataModel(null, "fileName", null, null, null, 2L, null)),
        Arguments.of(
            new DocumentMetadataModel(
                null, "fileName", null, null, null, null, Map.of("key", "value"))));
  }
}
