/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import org.opensearch.client.opensearch.core.SearchRequest;

import java.util.List;

public abstract class OpensearchKeyFilteringDao<T, R> extends OpensearchSearchableDao<T, R> {

  public OpensearchKeyFilteringDao(OpensearchQueryDSLWrapper queryDSLWrapper, OpensearchRequestDSLWrapper requestDSLWrapper,
                                   RichOpenSearchClient richOpenSearchClient) {
    super(queryDSLWrapper, requestDSLWrapper, richOpenSearchClient);
  }

  public T byKey(Long key) {
    validateKey(key);

    List<R> results;
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
    SearchRequest.Builder request = requestDSLWrapper.searchRequestBuilder(getIndexName())
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
