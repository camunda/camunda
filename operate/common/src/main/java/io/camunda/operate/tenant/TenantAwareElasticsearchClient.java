/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.tenant;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import java.io.IOException;
import java.util.concurrent.Callable;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class TenantAwareElasticsearchClient
    implements TenantAwareClient<SearchRequest, SearchResponse> {

  @Autowired
  @Qualifier("esClient")
  private RestHighLevelClient defaultClient;

  @Autowired(required = false)
  private TenantCheckApplier<SearchRequest> tenantCheckApplier;

  @Override
  public SearchResponse search(SearchRequest searchRequest) throws IOException {
    return search(
        searchRequest,
        () -> {
          return defaultClient.search(searchRequest, RequestOptions.DEFAULT);
        });
  }

  @Override
  public <C> C search(SearchRequest searchRequest, Callable<C> searchExecutor) throws IOException {
    applyTenantCheckIfPresent(searchRequest);
    try {
      return searchExecutor.call();
    } catch (IOException ioe) {
      throw ioe;
    } catch (RuntimeException re) {
      throw re;
    } catch (Exception e) {
      final var message =
          String.format("Unexpectedly failed to execute search request with %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  private void applyTenantCheckIfPresent(final SearchRequest searchRequest) {
    if (tenantCheckApplier != null) {
      tenantCheckApplier.apply(searchRequest);
    }
  }
}
