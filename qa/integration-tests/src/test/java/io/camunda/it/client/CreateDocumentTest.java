/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class CreateDocumentTest {

  private static ZeebeClient zeebeClient;

  @TestZeebe(initMethod = "initTestStandaloneCamunda")
  private static TestStandaloneCamunda testStandaloneCamunda;

  @SuppressWarnings("unused")
  static void initTestStandaloneCamunda() {
    testStandaloneCamunda = new TestStandaloneCamunda().withCamundaExporter();
  }

  @Test
  public void shouldCreateDocumentFromInputStream() {
    // given
    zeebeClient = testStandaloneCamunda.newClientBuilder().build();
    final var documentContent = new ByteArrayInputStream("test".getBytes());

    // when
    final var documentReference =
        zeebeClient.newCreateDocumentCommand().content(documentContent).send().join();

    // then
    assertThat(documentReference).isNotNull();
  }

  @Test
  public void shouldCreateDocumentFromByteArray() {
    // given
    zeebeClient = testStandaloneCamunda.newClientBuilder().build();
    final var documentContent = "test".getBytes();

    // when
    final var documentReference =
        zeebeClient.newCreateDocumentCommand().content(documentContent).send().join();

    // then
    assertThat(documentReference).isNotNull();
  }

  @Test
  public void shouldThrowIfContentIsNull() {
    // given
    zeebeClient = testStandaloneCamunda.newClientBuilder().build();

    // when
    final var exception =
        assertThrowsExactly(
            IllegalArgumentException.class,
            () -> zeebeClient.newCreateDocumentCommand().content((InputStream) null).send().join());

    // then
    assertThat(exception).isInstanceOf(IllegalArgumentException.class);
    assertThat(exception.getMessage()).isEqualTo("content must not be null");
  }

  @Test
  public void shouldUseTheProvidedDocumentId() {
    // given
    zeebeClient = testStandaloneCamunda.newClientBuilder().build();
    final var documentContent = "test";
    final var documentId = "test";

    // when
    final var documentReference =
        zeebeClient
            .newCreateDocumentCommand()
            .content(documentContent)
            .documentId(documentId)
            .send()
            .join();

    // then
    assertThat(documentReference).isNotNull();
    assertThat(documentReference.getDocumentId()).isEqualTo(documentId);

    // when
    final var duplicateIdCommand =
        zeebeClient
            .newCreateDocumentCommand()
            .content(documentContent)
            .documentId(documentId)
            .send();

    final var exception = assertThrowsExactly(ProblemException.class, duplicateIdCommand::join);

    assertThat(exception.getMessage()).startsWith("Failed with code 409");
    assertThat(exception.details()).isNotNull();
    assertThat(exception.details().getStatus()).isEqualTo(409);
    assertThat(exception.details().getDetail()).isEqualTo("Document with id 'test' already exists");
  }

  @Test
  public void shouldUseTheProvidedStoreId() {
    // given
    zeebeClient = testStandaloneCamunda.newClientBuilder().build();
    final var documentContent = "test";
    final var storeId = "in-memory";

    // when
    final var documentReference =
        zeebeClient
            .newCreateDocumentCommand()
            .content(documentContent)
            .storeId(storeId)
            .send()
            .join();

    // then
    assertThat(documentReference).isNotNull();
    assertThat(documentReference.getStoreId()).isEqualTo(storeId);
  }

  @Test
  public void shouldReturnBadRequestForNonExistingStoreId() {
    // given
    zeebeClient = testStandaloneCamunda.newClientBuilder().build();
    final var documentContent = "test";
    final var storeId = "non-existing";

    // when
    final var createDocumentCommand =
        zeebeClient.newCreateDocumentCommand().content(documentContent).storeId(storeId).send();
    final var exception = assertThrowsExactly(ProblemException.class, createDocumentCommand::join);

    // then
    assertThat(exception.getMessage()).startsWith("Failed with code 400");
    assertThat(exception.details()).isNotNull();
    assertThat(exception.details().getStatus()).isEqualTo(400);
    assertThat(exception.details().getDetail())
        .isEqualTo("Document store with id 'non-existing' does not exist");
  }

  @Test
  public void shouldIncludeFileName() {
    // given
    zeebeClient = testStandaloneCamunda.newClientBuilder().build();
    final var documentContent = "test";
    final var fileName = "test.txt";

    // when
    final var documentReference =
        zeebeClient
            .newCreateDocumentCommand()
            .content(documentContent)
            .fileName(fileName)
            .send()
            .join();

    // then
    assertThat(documentReference.getMetadata().getFileName()).isEqualTo(fileName);
  }

  @Test
  public void shouldIncludeTimeToLive() {
    // given
    zeebeClient = testStandaloneCamunda.newClientBuilder().build();
    final var documentContent = "test";
    final var timeToLive = Duration.ofMinutes(1);

    // when
    final var documentReference =
        zeebeClient
            .newCreateDocumentCommand()
            .content(documentContent)
            .timeToLive(timeToLive)
            .send()
            .join();

    // then
    assertThat(documentReference.getMetadata().getExpiresAt())
        .isCloseTo(OffsetDateTime.now().plus(timeToLive), within(1, ChronoUnit.SECONDS));
  }

  @Test
  public void shouldIncludeContentType() {
    // given
    zeebeClient = testStandaloneCamunda.newClientBuilder().build();
    final var documentContent = "test";
    final var contentType = "text/plain";

    // when
    final var documentReference =
        zeebeClient
            .newCreateDocumentCommand()
            .content(documentContent)
            .contentType(contentType)
            .send()
            .join();

    // then
    assertThat(documentReference).isNotNull();
    assertThat(documentReference.getMetadata().getContentType()).isEqualTo(contentType);
  }

  @Test
  public void shouldCalculateSize() {
    // given
    zeebeClient = testStandaloneCamunda.newClientBuilder().build();
    final var documentContent = "test";

    // when
    final var documentReference =
        zeebeClient.newCreateDocumentCommand().content(documentContent).send().join();

    // then
    assertThat(documentReference).isNotNull();
    assertThat(documentReference.getMetadata().getSize()).isEqualTo(4);
  }

  @Test
  public void shouldAddCustomMetadata() {
    // given
    zeebeClient = testStandaloneCamunda.newClientBuilder().build();
    final var documentContent = "test";
    final String key = "key1";
    final String value = "value1";
    final Map<String, Object> customMetadataMap = Map.of("key2", "value2");

    // when
    final var documentReference =
        zeebeClient
            .newCreateDocumentCommand()
            .content(documentContent)
            .customMetadata(key, value)
            .customMetadata(customMetadataMap)
            .send()
            .join();

    // then
    assertThat(documentReference).isNotNull();
    assertThat(documentReference.getMetadata().getCustomProperties().get("key1"))
        .isEqualTo("value1");
    assertThat(documentReference.getMetadata().getCustomProperties().get("key2"))
        .isEqualTo("value2");
  }
}
