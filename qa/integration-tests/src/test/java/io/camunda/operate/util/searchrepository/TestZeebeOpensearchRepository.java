/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util.searchrepository;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.store.opensearch.client.sync.ZeebeRichOpenSearchClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;

@Component
@Conditional(OpensearchCondition.class)
public class TestZeebeOpensearchRepository implements TestZeebeRepository {
  @Autowired
  protected ZeebeRichOpenSearchClient zeebeRichOpenSearchClient;

  @Override
  public <R> List<R> scrollTerm(String index, String field, long value, Class<R> clazz) {
    var requestBuilder = searchRequestBuilder(index)
      .query(term(field, value));

    return zeebeRichOpenSearchClient.doc().scrollValues(requestBuilder, clazz);
  }
}
