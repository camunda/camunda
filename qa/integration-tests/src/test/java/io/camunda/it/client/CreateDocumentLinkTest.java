/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.qa.util.multidb.MultiDbTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class CreateDocumentLinkTest {

  private static final String DOCUMENT_CONTENT = "test";

  private static CamundaClient camundaClient;
  private static DocumentReferenceResponse documentReference;

  @BeforeAll
  public static void beforeAll() {
    documentReference =
        camundaClient.newCreateDocumentCommand().content(DOCUMENT_CONTENT).send().join();
  }

  @Test
  public void shouldReturnBadRequestWhenDocumentStoreDoesNotExist() {
    // given
    final var storeId = "invalid-document-store-id";

    // when
    final var exception =
        assertThrowsExactly(
            ProblemException.class,
            () ->
                camundaClient
                    .newCreateDocumentLinkCommand(documentReference)
                    .storeId(storeId)
                    .send()
                    .join());

    // then
    assertThat(exception.getMessage()).startsWith("Failed with code 400");
    assertThat(exception.details().getStatus()).isEqualTo(400);
    assertThat(exception.details().getDetail())
        .contains("Document store with id 'invalid-document-store-id' does not exist");
  }

  @Test
  public void shouldReturnMethodNotAllowedWhenStoreIsInMemory() {
    // given
    final var storeId = "in-memory";
    final var documentId = documentReference.getDocumentId();

    // when
    final var exception =
        assertThrowsExactly(
            ProblemException.class,
            () ->
                camundaClient
                    .newCreateDocumentLinkCommand(documentId)
                    .storeId(storeId)
                    .contentHash(documentReference.getContentHash())
                    .send()
                    .join());

    // then
    assertThat(exception.getMessage()).startsWith("Failed with code 405");
    assertThat(exception.details().getStatus()).isEqualTo(405);
    assertThat(exception.details().getDetail())
        .contains("The in-memory document store does not support creating links");
  }
}
