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

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class CreateDocumentTest {

  private static CamundaClient camundaClient;

  @Test
  public void shouldCreateDocumentFromInputStream() {
    // given
    final var documentContent = new ByteArrayInputStream("test".getBytes());

    // when
    final var documentReference =
        camundaClient.newCreateDocumentCommand().content(documentContent).send().join();

    // then
    assertThat(documentReference).isNotNull();
  }

  @Test
  public void shouldCreateDocumentFromByteArray() {
    // given
    final var documentContent = "test".getBytes();

    // when
    final var documentReference =
        camundaClient.newCreateDocumentCommand().content(documentContent).send().join();

    // then
    assertThat(documentReference).isNotNull();
  }

  @Test
  public void shouldThrowIfContentIsNull() {
    // given

    // when
    final var exception =
        assertThrowsExactly(
            IllegalArgumentException.class,
            () ->
                camundaClient.newCreateDocumentCommand().content((InputStream) null).send().join());

    // then
    assertThat(exception).isInstanceOf(IllegalArgumentException.class);
    assertThat(exception.getMessage()).isEqualTo("content must not be null");
  }

  @Test
  public void shouldUseTheProvidedDocumentId() {
    // given

    final var documentContent = "test";
    final var documentId = "test";

    // when
    final var documentReference =
        camundaClient
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
        camundaClient
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
    final var documentContent = "test";
    final var storeId = "in-memory";

    // when
    final var documentReference =
        camundaClient
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
    final var documentContent = "test";
    final var storeId = "non-existing";

    // when
    final var createDocumentCommand =
        camundaClient.newCreateDocumentCommand().content(documentContent).storeId(storeId).send();
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
    final var documentContent = "test";
    final var fileName = "test.txt";

    // when
    final var documentReference =
        camundaClient
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
    final var documentContent = "test";
    final var timeToLive = Duration.ofMinutes(1);

    // when
    final var documentReference =
        camundaClient
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
    final var documentContent = "test";
    final var contentType = "text/plain";

    // when
    final var documentReference =
        camundaClient
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
    final var documentContent = "test";

    // when
    final var documentReference =
        camundaClient.newCreateDocumentCommand().content(documentContent).send().join();

    // then
    assertThat(documentReference).isNotNull();
    assertThat(documentReference.getMetadata().getSize()).isEqualTo(4);
  }

  @Test
  public void shouldAddCustomMetadata() {
    // given
    final var documentContent = "test";
    final String key = "key1";
    final String value = "value1";
    final Map<String, Object> customMetadataMap = Map.of("key2", "value2");

    // when
    final var documentReference =
        camundaClient
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

  @Test
  public void shouldAddProcessDefinitionId() {
    // given
    final var documentContent = "test";
    final String processDefinitionId = "test";

    // when
    final var documentReference =
        camundaClient
            .newCreateDocumentCommand()
            .content(documentContent)
            .processDefinitionId(processDefinitionId)
            .send()
            .join();

    // then
    assertThat(documentReference).isNotNull();
    assertThat(documentReference.getMetadata().getProcessDefinitionId())
        .isEqualTo(processDefinitionId);
  }

  @Test
  public void shouldAddProcessInstanceKey() {
    // given
    final var documentContent = "test";
    final long processInstanceKey = 1;

    // when
    final var documentReference =
        camundaClient
            .newCreateDocumentCommand()
            .content(documentContent)
            .processInstanceKey(processInstanceKey)
            .send()
            .join();

    // then
    assertThat(documentReference).isNotNull();
    assertThat(documentReference.getMetadata().getProcessInstanceKey())
        .isEqualTo(processInstanceKey);
  }
}
