/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.it.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.it.ArchiverITRepository;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.and;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.constantScore;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.ids;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.stringTerms;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.operate.util.CollectionUtil.toSafeArrayOfStrings;

@Component
@Conditional(OpensearchCondition.class)
public class OpensearchArchiverITRepository implements ArchiverITRepository {
  @Autowired
  private RichOpenSearchClient richOpenSearchClient;

  @Override
  public List<BatchOperationEntity> getBatchOperationEntities(String indexName, List<String> ids) throws IOException {
    var searchRequestBuilder = searchRequestBuilder(indexName)
      .query(constantScore(ids(toSafeArrayOfStrings(ids))))
      .size(100);

    return richOpenSearchClient.doc().searchValues(searchRequestBuilder, BatchOperationEntity.class, true);
  }

  @Override
  public List<ProcessInstanceForListViewEntity> getProcessInstances(String indexName, List<Long> ids) throws IOException {
    var searchRequestBuilder = searchRequestBuilder(indexName)
      .query(
        constantScore(
          and(
            ids(toSafeArrayOfStrings(ids)),
            term(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION)
          )
        )
      )
      .size(100);

    return richOpenSearchClient.doc().searchValues(searchRequestBuilder, ProcessInstanceForListViewEntity.class, true);
  }

  @Override
  public Optional<List<Long>> getIds(String indexName, String idFieldName, List<Long> ids, boolean ignoreAbsentIndex) throws IOException {
    try {
      var searchRequestBuilder = searchRequestBuilder(indexName)
        .query(stringTerms(idFieldName, Arrays.asList(toSafeArrayOfStrings(ids))))
        .size(100);

      List<Long> indexIds = richOpenSearchClient.doc().scrollValues(searchRequestBuilder, HashMap.class)
        .stream()
        .map(map -> (Long) map.get(idFieldName))
        .toList();

      return Optional.of(indexIds);
    } catch (OpenSearchException ex) {
      if (!ex.getMessage().contains("index_not_found_exception") || !ignoreAbsentIndex) {
        throw ex;
      }
      return Optional.empty();
    }
  }
}
