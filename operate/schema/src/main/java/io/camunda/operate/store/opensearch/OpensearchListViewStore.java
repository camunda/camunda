/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.opensearch;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.operate.util.CollectionUtil.map;
import static io.camunda.operate.util.CollectionUtil.toSafeListOfStrings;
import static io.camunda.operate.util.ExceptionHelper.withIOException;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.store.ListViewStore;
import io.camunda.operate.store.NotFoundException;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.store.opensearch.dsl.RequestDSL;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchListViewStore implements ListViewStore {

  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired private RichOpenSearchClient richOpenSearchClient;

  @Autowired private OperateProperties operateProperties;

  @Override
  public Map<Long, String> getListViewIndicesForProcessInstances(final List<Long> processInstanceIds)
      throws IOException {
    final var searchRequestBuilder =
        searchRequestBuilder(listViewTemplate, RequestDSL.QueryType.ALL)
            .query(
                withTenantCheck(
                    ids(toSafeListOfStrings(map(processInstanceIds, Object::toString)))));

    final Map<Long, String> processInstanceId2IndexName =
        withIOException(
            () ->
                richOpenSearchClient
                    .doc()
                    .search(searchRequestBuilder, Void.class)
                    .hits()
                    .hits()
                    .stream()
                    .collect(Collectors.toMap(hit -> Long.valueOf(hit.id()), Hit::index)));

    if (processInstanceId2IndexName.isEmpty()) {
      throw new NotFoundException(
          String.format("Process instances %s doesn't exists.", processInstanceIds));
    }

    return processInstanceId2IndexName;
  }

  @Override
  public String findProcessInstanceTreePathFor(final long processInstanceKey) {
    record Result(String treePath) {}
    final RequestDSL.QueryType queryType =
        operateProperties.getImporter().isReadArchivedParents()
            ? RequestDSL.QueryType.ALL
            : RequestDSL.QueryType.ONLY_RUNTIME;
    final var searchRequestBuilder =
        searchRequestBuilder(listViewTemplate, queryType)
            .query(withTenantCheck(term(ListViewTemplate.KEY, processInstanceKey)))
            .source(sourceInclude(ListViewTemplate.TREE_PATH));

    final List<Hit<Result>> hits =
        richOpenSearchClient.doc().search(searchRequestBuilder, Result.class).hits().hits();

    if (hits.size() > 0) {
      return hits.get(0).source().treePath();
    }
    return null;
  }

  @Override
  public List<Long> getProcessInstanceKeysWithEmptyProcessVersionFor(final Long processDefinitionKey) {
    final var searchRequestBuilder =
        searchRequestBuilder(listViewTemplate.getAlias())
            .query(
                withTenantCheck(
                    constantScore(
                        and(
                            term(ListViewTemplate.PROCESS_KEY, processDefinitionKey),
                            not(exists(ListViewTemplate.PROCESS_VERSION))))))
            .source(s -> s.fetch(false));

    return richOpenSearchClient
        .doc()
        .search(searchRequestBuilder, Void.class)
        .hits()
        .hits()
        .stream()
        .map(hit -> Long.valueOf(hit.id()))
        .toList();
  }
}
