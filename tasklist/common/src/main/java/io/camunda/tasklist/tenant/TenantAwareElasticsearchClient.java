/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.tenant;

import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.Callable;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticSearchCondition.class)
@Component
public class TenantAwareElasticsearchClient {

  @Autowired
  @Qualifier("tasklistEsClient")
  private RestHighLevelClient defaultClient;

  @Autowired(required = false)
  private TenantCheckApplier<SearchRequest> tenantCheckApplier;

  public SearchResponse search(SearchRequest searchRequest) throws IOException {
    return search(searchRequest, () -> defaultClient.search(searchRequest, RequestOptions.DEFAULT));
  }

  public SearchResponse searchByTenantIds(SearchRequest searchRequest, Collection<String> tenantIds)
      throws IOException {
    return search(
        () -> tenantCheckApplier.apply(searchRequest, tenantIds),
        () -> defaultClient.search(searchRequest, RequestOptions.DEFAULT));
  }

  public <C> C search(SearchRequest searchRequest, Callable<C> searchExecutor) throws IOException {
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
