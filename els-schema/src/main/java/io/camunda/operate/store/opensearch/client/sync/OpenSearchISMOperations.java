/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.opensearch.client.sync;

import io.camunda.operate.opensearch.ExtendedOpenSearchClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;

import java.util.Map;
import java.util.function.Function;

import static java.lang.String.format;

public class OpenSearchISMOperations extends OpenSearchRetryOperation {
  public OpenSearchISMOperations(Logger logger, OpenSearchClient openSearchClient) {
    super(logger, openSearchClient);
  }

  private <R> R withExtendedOpenSearchClient(Function<ExtendedOpenSearchClient, R> f) {
    if (openSearchClient instanceof ExtendedOpenSearchClient extendedOpenSearchClient) {
      return f.apply(extendedOpenSearchClient);
    } else {
      throw new UnsupportedOperationException("ExtendedOpenSearchClient is required! Provided: " + openSearchClient.getClass().getName());
    }
  }

  public Map<String, Object> addPolicyToIndex(String index, String policy) {
    var json = format("{\"policy_id\": \"%s\"}", policy);
    return withExtendedOpenSearchClient( extendedOpenSearchClient ->
      safe(
        () -> extendedOpenSearchClient.arbitraryRequest("POST", "/_plugins/_ism/add/" + index, json),
        e -> format("Failed to add policy %s to index %s", policy, index)
      ));
  }

  public Map<String, Object> createPolicy(String policyName, String policyJson) {
    return withExtendedOpenSearchClient( extendedOpenSearchClient ->
      safe(
        () -> extendedOpenSearchClient.arbitraryRequest("PUT", "/_plugins/_ism/policies/" + policyName, policyJson),
        e -> format("Failed to create policy: %s", policyName)
    ));
  }

  public Map<String, Object> deletePolicy(String policyName) {
    return withExtendedOpenSearchClient( extendedOpenSearchClient ->
      safe(
        () -> extendedOpenSearchClient.arbitraryRequest("DELETE", "/_plugins/_ism/policies/" + policyName, "{}"),
        e -> format("Failed to delete policy: %s", policyName)
      ));
  }

  public Map<String, Object> getPolicy(String policyName) {
    return withExtendedOpenSearchClient( extendedOpenSearchClient ->
      safe(
        () -> extendedOpenSearchClient.arbitraryRequest("GET", "/_plugins/_ism/policies/" + policyName, "{}"),
        e -> format("Failed to get policy: %s", policyName)
    ));
  }
}
