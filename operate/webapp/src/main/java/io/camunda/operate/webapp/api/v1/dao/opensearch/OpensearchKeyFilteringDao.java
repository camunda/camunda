/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import java.util.List;
import org.opensearch.client.opensearch.core.SearchRequest;

public abstract class OpensearchKeyFilteringDao<T, R> extends OpensearchSearchableDao<T, R> {

  public OpensearchKeyFilteringDao(
      OpensearchQueryDSLWrapper queryDSLWrapper,
      OpensearchRequestDSLWrapper requestDSLWrapper,
      RichOpenSearchClient richOpenSearchClient) {
    super(queryDSLWrapper, requestDSLWrapper, richOpenSearchClient);
  }

  public T byKey(Long key) {
    validateKey(key);

    final List<R> results;
    try {
      results = searchByKey(key);
    } catch (Exception e) {
      throw new ServerException(getByKeyServerReadErrorMessage(key), e);
    }
    if (results == null || results.isEmpty()) {
      throw new ResourceNotFoundException(getByKeyNoResultsErrorMessage(key));
    }
    if (results.size() > 1) {
      throw new ServerException(getByKeyTooManyResultsErrorMessage(key));
    }
    return convertInternalToApiResult(results.get(0));
  }

  protected List<R> searchByKey(Long key) {
    final SearchRequest.Builder request =
        requestDSLWrapper
            .searchRequestBuilder(getIndexName())
            .query(queryDSLWrapper.withTenantCheck(queryDSLWrapper.term(getKeyFieldName(), key)));

    return richOpenSearchClient.doc().searchValues(request, getInternalDocumentModelClass());
  }

  protected void validateKey(Long key) {
    if (key == null) {
      throw new ServerException("Key provided cannot be null");
    }
  }

  protected abstract String getKeyFieldName();

  protected abstract String getByKeyServerReadErrorMessage(Long key);

  protected abstract String getByKeyNoResultsErrorMessage(Long key);

  protected abstract String getByKeyTooManyResultsErrorMessage(Long key);
}
