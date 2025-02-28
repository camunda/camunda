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
import java.io.InputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class GetDocumentContentTest {

  private static final String DOCUMENT_CONTENT = "test";

  private static CamundaClient camundaClient;
  private static DocumentReferenceResponse documentReference;

  @BeforeAll
  public static void beforeAll() {
    documentReference =
        camundaClient.newCreateDocumentCommand().content(DOCUMENT_CONTENT).send().join();
  }

  @Test
  public void shouldWorkWithDocumentReference() {
    // given

    // when
    try (final InputStream is =
        camundaClient.newDocumentContentGetRequest(documentReference).send().join()) {
      // then
      final String content = new String(is.readAllBytes());
      assertThat(content).isEqualTo(DOCUMENT_CONTENT);

    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void shouldReturnNotFoundIfDocumentDoesNotExist() {
    // given
    final var documentId = "non-existing-document";

    // when
    final var command = camundaClient.newDocumentContentGetRequest(documentId).send();

    // then
    final var exception = assertThrowsExactly(ProblemException.class, command::join);
    assertThat(exception.getMessage()).startsWith("Failed with code 404");
    assertThat(exception.details()).isNotNull();
    assertThat(exception.details().getStatus()).isEqualTo(404);
    assertThat(exception.details().getDetail())
        .isEqualTo("Document with id 'non-existing-document' not found");
  }

  @Test
  public void shouldReturnBadRequestIfDocumentStoreIdDoesNotExist() {
    // given
    final var documentId = documentReference.getDocumentId();
    final var storeId = "non-existing-store";

    // when
    final var command =
        camundaClient.newDocumentContentGetRequest(documentId).storeId(storeId).send();

    // then
    final var exception = assertThrowsExactly(ProblemException.class, command::join);
    assertThat(exception.getMessage()).startsWith("Failed with code 400");
    assertThat(exception.details()).isNotNull();
    assertThat(exception.details().getStatus()).isEqualTo(400);
    assertThat(exception.details().getDetail())
        .isEqualTo("Document store with id 'non-existing-store' does not exist");
  }
}
