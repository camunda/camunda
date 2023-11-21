/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.exceptions.ValidationException;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import org.opensearch.client.opensearch._types.query_dsl.Query;

import static io.camunda.operate.util.ConversionUtils.stringIsEmpty;

public abstract class OpensearchDao {
  protected final OpensearchQueryDSLWrapper queryDSLWrapper;
  protected final OpensearchRequestDSLWrapper requestDSLWrapper;
  protected final RichOpenSearchClient richOpenSearchClient;

  public OpensearchDao(OpensearchQueryDSLWrapper queryDSLWrapper, OpensearchRequestDSLWrapper requestDSLWrapper,
                       RichOpenSearchClient richOpenSearchClient) {
    this.queryDSLWrapper = queryDSLWrapper;
    this.requestDSLWrapper = requestDSLWrapper;
    this.richOpenSearchClient = richOpenSearchClient;
  }

  protected Query buildTermQuery(String name, Number value) {
    if (value != null) {
      if (value instanceof Long) {
        return queryDSLWrapper.term(name, value.longValue());
      } else if (value instanceof Integer) {
        return queryDSLWrapper.term(name, value.intValue());
      } else {
        throw new ValidationException("Type " + value.getClass().getName() + " not supported");
      }
    }
    return null;
  }

  protected Query buildTermQuery(final String name, final String value) {
    if (!stringIsEmpty(value)) {
      return queryDSLWrapper.term(name, value);
    }
    return null;
  }
}
