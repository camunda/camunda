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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class DeleteDocumentTest {

  private static final String DOCUMENT_CONTENT = "test";

  private static CamundaClient camundaClient;

  private DocumentReferenceResponse documentReference;

  @BeforeEach
  public void beforeEach() {
    documentReference =
        camundaClient.newCreateDocumentCommand().content(DOCUMENT_CONTENT).send().join();
  }

  @Test
  public void shouldWorkWithDocumentId() {
    // given
    final var documentId = documentReference.getDocumentId();

    // when
    camundaClient.newDeleteDocumentCommand(documentId).send().join();

    // then
    assertDocumentIsDeleted(documentId);
  }

  @Test
  public void shouldWorkWithDocumentIdAndStoreId() {
    // given
    final var documentId = documentReference.getDocumentId();
    final var storeId = documentReference.getStoreId();

    // when
    camundaClient.newDeleteDocumentCommand(documentId).storeId(storeId).send().join();

    // then
    assertDocumentIsDeleted(documentId);
  }

  @Test
  public void shouldWorkWithDocumentReference() {
    // given

    // when
    camundaClient.newDeleteDocumentCommand(documentReference).send().join();

    // then
    assertDocumentIsDeleted(documentReference.getDocumentId());
  }

  @Test
  public void shouldReturnNotFoundIfDocumentDoesNotExist() {
    // given
    final var documentId = "non-existing-document";

    // when
    final var exception =
        assertThrowsExactly(
            ProblemException.class,
            () -> {
              camundaClient.newDeleteDocumentCommand(documentId).send().join();
            });

    // then
    assertThat(exception.details().getStatus()).isEqualTo(404);
    assertThat(exception.details().getDetail())
        .isEqualTo("Document with id 'non-existing-document' not found");
  }

  @Test
  public void shouldReturnBadRequestForNonExistingStoreId() {
    // given
    final var documentContent = "test";
    final var storeId = "non-existing";

    // when
    final var command =
        camundaClient.newCreateDocumentCommand().content(documentContent).storeId(storeId).send();

    // then
    final var exception = assertThrowsExactly(ProblemException.class, command::join);
    assertThat(exception.getMessage()).startsWith("Failed with code 400");
    assertThat(exception.details()).isNotNull();
    assertThat(exception.details().getStatus()).isEqualTo(400);
    assertThat(exception.details().getDetail())
        .isEqualTo("Document store with id 'non-existing' does not exist");
  }

  private void assertDocumentIsDeleted(final String documentId) {
    final var exception =
        assertThrowsExactly(
            ProblemException.class,
            () -> camundaClient.newDocumentContentGetRequest(documentId).send().join());
    assertThat(exception.details().getStatus()).isEqualTo(404);
  }
}
