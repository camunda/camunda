/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.es.backup.os;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.property.TasklistProperties;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.endpoints.SimpleEndpoint;

@ExtendWith(MockitoExtension.class)
class BackupManagerOpenSearchTest {

  @Mock private OpenSearchAsyncClient openSearchAsyncClient;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private TasklistProperties tasklistProperties;

  @InjectMocks private BackupManagerOpenSearch backupManagerOpenSearch;

  @Test
  void shouldValidateRepositoryExistsDoNotDeserializeOpenSearchResponse() throws IOException {
    final OpenSearchTransport openSearchTransport = mock(OpenSearchTransport.class);
    when(openSearchAsyncClient._transport()).thenReturn(openSearchTransport);
    when(openSearchTransport.performRequestAsync(any(), any(), any()))
        .thenReturn(mock(CompletableFuture.class));

    backupManagerOpenSearch.validateRepositoryExists();

    final ArgumentCaptor<SimpleEndpoint> endpointArgumentCaptor =
        ArgumentCaptor.forClass(SimpleEndpoint.class);
    verify(openSearchTransport).performRequestAsync(any(), endpointArgumentCaptor.capture(), any());
    assertThat(endpointArgumentCaptor.getValue().responseDeserializer()).isNull();
  }
}
