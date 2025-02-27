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
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.client.CamundaClient;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class CreateDocumentBatchTest {

  private static CamundaClient camundaClient;

  @Test
  public void shouldCreateDocumentsFromInputStreams() {
    // given
    final var documentContent1 = new ByteArrayInputStream("test one".getBytes());
    final var documentContent2 = new ByteArrayInputStream("test two".getBytes());

    // when
    final var response =
        camundaClient
            .newCreateDocumentBatchCommand()
            .addDocument()
            .content(documentContent1)
            .fileName("test1.txt")
            .done()
            .addDocument()
            .content(documentContent2)
            .fileName("test2.txt")
            .done()
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.isSuccessful()).isTrue();
    assertThat(response.getCreatedDocuments()).hasSize(2);
  }

  @Test
  public void shouldCreateDocumentsFromDifferentSources() {
    // given
    final var documentContent1 = new ByteArrayInputStream("test one".getBytes());
    final var documentContent2 = "test two".getBytes();

    // when
    final var response =
        camundaClient
            .newCreateDocumentBatchCommand()
            .addDocument()
            .fileName("test1.txt")
            .content(documentContent1)
            .done()
            .addDocument()
            .fileName("test2.txt")
            .content(documentContent2)
            .done()
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.isSuccessful()).isTrue();
    assertThat(response.getCreatedDocuments()).hasSize(2);
  }

  @Test
  public void shouldCreateDocumentsWithBasicMetadata() {
    // given
    final var documentContent1 = new ByteArrayInputStream("test one".getBytes());
    final var documentContent2 = new ByteArrayInputStream("test two".getBytes());

    // when
    final var response =
        camundaClient
            .newCreateDocumentBatchCommand()
            .addDocument()
            .content(documentContent1)
            .contentType("text/plain")
            .fileName("test1.txt")
            .timeToLive(Duration.ofMinutes(1))
            .done()
            .addDocument()
            .content(documentContent2)
            .timeToLive(Duration.ofMinutes(2))
            .contentType("application/pdf")
            .fileName("test2.pdf")
            .done()
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.isSuccessful()).isTrue();
    assertThat(response.getCreatedDocuments()).hasSize(2);

    final var document1 =
        response.getCreatedDocuments().stream()
            .filter(d -> d.getMetadata().getFileName().equals("test1.txt"))
            .findFirst()
            .get();
    assertThat(document1.getMetadata().getContentType()).isEqualTo("text/plain");
    assertThat(document1.getMetadata().getExpiresAt())
        .isCloseTo(OffsetDateTime.now().plus(Duration.ofMinutes(1)), within(1, ChronoUnit.SECONDS));

    final var document2 =
        response.getCreatedDocuments().stream()
            .filter(d -> d.getMetadata().getFileName().equals("test2.pdf"))
            .findFirst()
            .get();
    assertThat(document2.getMetadata().getContentType()).isEqualTo("application/pdf");
    assertThat(document2.getMetadata().getExpiresAt())
        .isCloseTo(OffsetDateTime.now().plus(Duration.ofMinutes(2)), within(1, ChronoUnit.SECONDS));
  }

  @Test
  public void shouldCreateDocumentsWithCustomMetadata() {
    // given
    final var documentContent1 = new ByteArrayInputStream("test one".getBytes());
    final var documentContent2 = new ByteArrayInputStream("test two".getBytes());

    // when
    final var response =
        camundaClient
            .newCreateDocumentBatchCommand()
            .addDocument()
            .content(documentContent1)
            .fileName("test1.pdf")
            .customMetadata("key1", "value1")
            .customMetadata("key2", 2)
            .done()
            .addDocument()
            .content(documentContent2)
            .fileName("test2.pdf")
            .customMetadata("key3", "value3")
            .customMetadata("key4", 4)
            .done()
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.isSuccessful()).isTrue();
    assertThat(response.getCreatedDocuments()).hasSize(2);

    final var document1 =
        response.getCreatedDocuments().stream()
            .filter(d -> d.getMetadata().getCustomProperties().containsKey("key1"))
            .findFirst()
            .get();
    assertThat(document1.getMetadata().getCustomProperties()).hasSize(2);
    assertThat(document1.getMetadata().getCustomProperties().get("key1")).isEqualTo("value1");
    assertThat(document1.getMetadata().getCustomProperties().get("key2")).isEqualTo(2);

    final var document2 =
        response.getCreatedDocuments().stream()
            .filter(d -> d.getMetadata().getCustomProperties().containsKey("key3"))
            .findFirst()
            .get();
    assertThat(document2.getMetadata().getCustomProperties()).hasSize(2);
    assertThat(document2.getMetadata().getCustomProperties().get("key3")).isEqualTo("value3");
    assertThat(document2.getMetadata().getCustomProperties().get("key4")).isEqualTo(4);
  }

  @Test
  public void shouldThrowIfContentIsMissing() {
    // given

    // when
    final var command = camundaClient.newCreateDocumentBatchCommand().addDocument().done();

    // then
    final var exception = assertThrows(IllegalArgumentException.class, command::send);
    assertThat(exception).hasMessageContaining("content");
  }

  @Test
  public void shouldCalculateSize() {
    // given
    final var documentContent1 = new ByteArrayInputStream("test one".getBytes());
    final var documentContent2 = new ByteArrayInputStream("test two".getBytes());

    // when
    final var response =
        camundaClient
            .newCreateDocumentBatchCommand()
            .addDocument()
            .content(documentContent1)
            .fileName("test1.txt")
            .done()
            .addDocument()
            .fileName("test2.txt")
            .content(documentContent2)
            .done()
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.isSuccessful()).isTrue();
    assertThat(response.getCreatedDocuments()).hasSize(2);

    final var document1 = response.getCreatedDocuments().get(0);
    assertThat(document1.getMetadata().getSize()).isEqualTo(8);

    final var document2 = response.getCreatedDocuments().get(1);
    assertThat(document2.getMetadata().getSize()).isEqualTo(8);
  }

  @Test
  public void shouldUseProvidedStoreId() {
    // given
    final var documentContent = new ByteArrayInputStream("test one".getBytes());
    final var storeId = "in-memory";

    // when
    final var response =
        camundaClient
            .newCreateDocumentBatchCommand()
            .storeId(storeId)
            .addDocument()
            .fileName("test1.txt")
            .content(documentContent)
            .done()
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.isSuccessful()).isTrue();
    assertThat(response.getCreatedDocuments()).hasSize(1);
    assertThat(response.getCreatedDocuments().get(0).getStoreId()).isEqualTo(storeId);
  }

  @Test
  public void shouldReturnBadRequestForNonExistingStoreId() {
    // given
    final var documentContent = new ByteArrayInputStream("test one".getBytes());

    // when
    final var command =
        camundaClient
            .newCreateDocumentBatchCommand()
            .storeId("non-existing-store")
            .addDocument()
            .fileName("test1.txt")
            .content(documentContent)
            .done()
            .send();

    // then
    final var exception = assertThrows(Exception.class, command::join);
    assertThat(exception).hasMessageContaining("non-existing-store");
  }

  @Test
  public void shouldUseProvidedProcessDefinitionIdAndProcessInstanceKey() {
    // given
    final var documentContent1 = new ByteArrayInputStream("test one".getBytes());
    final var documentContent2 = new ByteArrayInputStream("test two".getBytes());

    final var processDefinitionId = "test-process-definition";
    final var processInstanceKey = 123L;

    // when
    final var response =
        camundaClient
            .newCreateDocumentBatchCommand()
            .processDefinitionId(processDefinitionId)
            .processInstanceKey(processInstanceKey)
            .addDocument()
            .fileName("test1.txt")
            .content(documentContent1)
            .done()
            .addDocument()
            .fileName("test2.txt")
            .content(documentContent2)
            .done()
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.isSuccessful()).isTrue();
    assertThat(response.getCreatedDocuments()).hasSize(2);

    response
        .getCreatedDocuments()
        .forEach(
            document -> {
              assertThat(document.getMetadata().getProcessDefinitionId())
                  .isEqualTo(processDefinitionId);
              assertThat(document.getMetadata().getProcessInstanceKey())
                  .isEqualTo(processInstanceKey);
            });
  }
}
