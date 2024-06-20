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
import io.camunda.search.connect.os.clients.dto.AddPolicyRequest;
import io.camunda.search.connect.os.clients.dto.DeleteStateManagementPolicyResponse;
import io.camunda.search.connect.os.clients.dto.GetIndexStateManagementPolicyResponse;
import io.camunda.search.connect.os.clients.dto.IndexPolicyResponse;
import io.camunda.search.connect.os.clients.dto.PutIndexStateManagementPolicyRequest;
import io.camunda.search.connect.os.clients.dto.PutIndexStateManagementPolicyResponse;
import java.io.IOException;
import java.util.Optional;
import org.opensearch.client.Request;
import org.opensearch.client.RestClient;

public class OpensearchIndexStateManagementClient extends OpensearchRestClientBased {

  public OpensearchIndexStateManagementClient(
      final RestClient client, final ObjectMapper objectMapper) {
    super(client, objectMapper);
  }

  public Optional<GetIndexStateManagementPolicyResponse> getIndexStateManagementPolicy(
      final String policyName) {
    try {
      final var request =
          new Request("GET", String.format("/_plugins/_ism/policies/%s", policyName));
      return Optional.of(sendRequest(request, GetIndexStateManagementPolicyResponse.class));
    } catch (final IOException e) {
      throw new CamundaSearchClientException("Failed to get index state management policy", e);
    }
  }

  public boolean deleteIndexStateManagementPolicy(final String policyName) {
    try {
      final var request =
          new Request("DELETE", String.format("/_plugins/_ism/policies/%s", policyName));
      final var response = sendRequest(request, DeleteStateManagementPolicyResponse.class);
      return response.result().equals(DeleteStateManagementPolicyResponse.DELETED);
    } catch (final IOException e) {
      throw new CamundaSearchClientException("Failed to delete index state management policy", e);
    }
  }

  public boolean putIndexStateManagementPolicy(
      final String policyName, final PutIndexStateManagementPolicyRequest policy) {
    try {
      final var request =
          new Request("PUT", String.format("/_plugins/_ism/policies/%s", policyName));
      request.setJsonEntity(objectMapper.writeValueAsString(policy));
      final var response = sendRequest(request, PutIndexStateManagementPolicyResponse.class);
      return response.policy() != null;
    } catch (final IOException e) {
      throw new CamundaSearchClientException("Failed to put index state management policy", e);
    }
  }

  public boolean addIndexStateManagementPolicyToIndex(final String index, final String policyName) {
    try {
      final var request = new Request("POST", String.format("/_plugins/_ism/add/%s", index));
      final var requestEntity = new AddPolicyRequest(policyName);
      request.setJsonEntity(objectMapper.writeValueAsString(requestEntity));
      final var response = sendRequest(request, IndexPolicyResponse.class);
      return !response.failures();
    } catch (final IOException e) {
      throw new CamundaSearchClientException("Failed to add policy to index", e);
    }
  }

  public boolean removeIndexStateManagementPolicyToIndex(final String index) {
    try {
      final var request = new Request("POST", String.format("/_plugins/_ism/remove/%s", index));
      final var response = sendRequest(request, IndexPolicyResponse.class);
      return !response.failures();
    } catch (final IOException e) {
      throw new CamundaSearchClientException("Failed to remove policy from indices", e);
    }
  }
}
