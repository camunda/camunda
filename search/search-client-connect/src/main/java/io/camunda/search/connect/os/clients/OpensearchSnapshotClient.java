/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.os.clients;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.search.clients.CamundaSearchClientException;
import io.camunda.search.connect.os.clients.dto.GetSnapshotResponse;
import java.io.IOException;
import java.util.HashMap;
import org.opensearch.client.Request;
import org.opensearch.client.RestClient;

public final class OpensearchSnapshotClient extends OpensearchRestClientBased {

  public OpensearchSnapshotClient(final RestClient client, final ObjectMapper objectMapper) {
    super(client, objectMapper);
  }

  public boolean getSnapshotRepository(final String repository) {
    try {
      final var request = new Request("GET", String.format("/_snapshot/%s", repository));
      final var response = sendRequest(request, HashMap.class);
      return !response.isEmpty();
    } catch (final IOException e) {
      throw new CamundaSearchClientException("Failed to remove policy from indices", e);
    }
  }

  public GetSnapshotResponse getSnapshot(final String repository, final String snapshot) {
    try {
      final var request =
          new Request("GET", String.format("/_snapshot/%s/%s", repository, snapshot));
      return sendRequest(request, GetSnapshotResponse.class);
    } catch (final IOException e) {
      throw new CamundaSearchClientException("Failed to remove policy from indices", e);
    }
  }
}
