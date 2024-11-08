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

import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.client.api.response.DocumentReferenceResponse;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class CreateDocumentLinkTest {

  private static final String DOCUMENT_CONTENT = "test";

  private static ZeebeClient zeebeClient;
  private static DocumentReferenceResponse documentReference;

  @TestZeebe(initMethod = "initTestStandaloneCamunda")
  private static TestStandaloneCamunda testStandaloneCamunda;

  @SuppressWarnings("unused")
  static void initTestStandaloneCamunda() {
    testStandaloneCamunda = new TestStandaloneCamunda().withCamundaExporter();
  }

  @BeforeAll
  public static void beforeAll() {
    zeebeClient = testStandaloneCamunda.newClientBuilder().build();
    documentReference =
        zeebeClient.newCreateDocumentCommand().content(DOCUMENT_CONTENT).send().join();
  }

  @Test
  public void shouldReturnBadRequestWhenDocumentStoreDoesNotExist() {
    // given
    final var storeId = "invalid-document-store-id";
    zeebeClient = testStandaloneCamunda.newClientBuilder().build();

    // when
    final var exception =
        assertThrowsExactly(
            ProblemException.class,
            () ->
                zeebeClient
                    .newCreateDocumentLinkCommand(documentReference.getDocumentId())
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
    zeebeClient = testStandaloneCamunda.newClientBuilder().build();

    // when
    final var exception =
        assertThrowsExactly(
            ProblemException.class,
            () ->
                zeebeClient
                    .newCreateDocumentLinkCommand(documentReference.getDocumentId())
                    .storeId(storeId)
                    .send()
                    .join());

    // then
    assertThat(exception.getMessage()).startsWith("Failed with code 405");
    assertThat(exception.details().getStatus()).isEqualTo(405);
    assertThat(exception.details().getDetail())
        .contains("The in-memory document store does not support creating links");
  }
}
