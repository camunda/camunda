/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.tenant;

import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.Callable;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpenSearchCondition.class)
@Component
public class TenantAwareOpenSearchClient {

  @Autowired
  @Qualifier("tasklistOsClient")
  private OpenSearchClient defaultClient;

  @Autowired(required = false)
  private TenantCheckApplier<SearchRequest.Builder> tenantCheckApplier;

  public <T> SearchResponse<T> search(SearchRequest.Builder searchRequest, Class<T> objectClass)
      throws IOException {
    return search(searchRequest, () -> defaultClient.search(searchRequest.build(), objectClass));
  }

  public <T> SearchResponse<T> searchByTenantIds(
      SearchRequest.Builder searchRequest, Class<T> clazz, Collection<String> tenantIds)
      throws IOException {
    return search(
        () -> tenantCheckApplier.apply(searchRequest, tenantIds),
        () -> defaultClient.search(searchRequest.build(), clazz));
  }

  public <C> C search(SearchRequest.Builder searchRequest, Callable<C> searchExecutor)
      throws IOException {
    return search(() -> tenantCheckApplier.apply(searchRequest), searchExecutor);
  }

  private <C> C search(Runnable tenantCheckApplier, Callable<C> searchExecutor) throws IOException {
    tenantCheckApplier.run();
    try {
      return searchExecutor.call();
    } catch (IOException ioe) {
      throw ioe;
    } catch (RuntimeException re) {
      throw re;
    } catch (Exception e) {
      final var message =
          String.format("Unexpectedly failed to execute search request with %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }
}
