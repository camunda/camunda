/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.tenant;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.Callable;

@Conditional(OpensearchCondition.class)
@Component
public class TenantAwareOpensearchClient implements TenantAwareClient<SearchRequest, SearchResponse> {

  @Autowired
  private OpenSearchClient openSearchClient;

  @Autowired(required = false)
  private TenantCheckApplier<SearchRequest> tenantCheckApplier;

  @Override
  public SearchResponse search(SearchRequest searchRequest) throws IOException {
    return search(searchRequest, () -> {
      return openSearchClient.search(searchRequest, Void.class);
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
      final var message = String.format("Unexpectedly failed to execute search request with %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  private void applyTenantCheckIfPresent(final SearchRequest searchRequest) {
    if (tenantCheckApplier != null) {
      tenantCheckApplier.apply(searchRequest);
    }
  }
}
