/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.opensearch.client.sync;

import static java.lang.String.format;

import java.util.Map;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;

public class OpenSearchISMOperations extends OpenSearchRetryOperation {
  public OpenSearchISMOperations(Logger logger, OpenSearchClient openSearchClient) {
    super(logger, openSearchClient);
  }

  public Map<String, Object> addPolicyToIndex(String index, String policy) {
    final var json = format("{\"policy_id\": \"%s\"}", policy);
    return withExtendedOpenSearchClient(
        extendedOpenSearchClient ->
            safe(
                () ->
                    extendedOpenSearchClient.arbitraryRequest(
                        "POST", "/_plugins/_ism/add/" + index, json),
                e -> format("Failed to add policy %s to index %s", policy, index)));
  }

  public Map<String, Object> createPolicy(String policyName, String policyJson) {
    return withExtendedOpenSearchClient(
        extendedOpenSearchClient ->
            safe(
                () ->
                    extendedOpenSearchClient.arbitraryRequest(
                        "PUT", "/_plugins/_ism/policies/" + policyName, policyJson),
                e -> format("Failed to create policy: %s", policyName)));
  }

  public Map<String, Object> deletePolicy(String policyName) {
    return withExtendedOpenSearchClient(
        extendedOpenSearchClient ->
            safe(
                () ->
                    extendedOpenSearchClient.arbitraryRequest(
                        "DELETE", "/_plugins/_ism/policies/" + policyName, "{}"),
                e -> format("Failed to delete policy: %s", policyName)));
  }

  public Map<String, Object> getPolicy(String policyName) {
    return withExtendedOpenSearchClient(
        extendedOpenSearchClient ->
            safe(
                () ->
                    extendedOpenSearchClient.arbitraryRequest(
                        "GET", "/_plugins/_ism/policies/" + policyName, "{}"),
                e -> format("Failed to get policy: %s", policyName)));
  }
}
