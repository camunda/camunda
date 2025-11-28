/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.elasticsearch;

import static io.camunda.operate.util.CollectionUtil.map;
import static io.camunda.operate.util.CollectionUtil.toSafeArrayOfStrings;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.store.ListViewStore;
import io.camunda.operate.store.NotFoundException;
import io.camunda.operate.util.ElasticsearchTenantHelper;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.util.ElasticsearchUtil.QueryType;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchListViewStore implements ListViewStore {

  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired private ElasticsearchClient es8Client;

  @Autowired private ElasticsearchTenantHelper tenantHelper;

  @Override
  public Map<Long, String> getListViewIndicesForProcessInstances(
      final List<Long> processInstanceIds) {
    final List<String> processInstanceIdsAsStrings = map(processInstanceIds, Object::toString);

    final var query = ElasticsearchUtil.idsQuery(toSafeArrayOfStrings(processInstanceIdsAsStrings));
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    final var searchRequestBuilder =
        new SearchRequest.Builder()
            .index(ElasticsearchUtil.whereToSearch(listViewTemplate, QueryType.ALL))
            .query(tenantAwareQuery);

    final var resStream =
        ElasticsearchUtil.scrollAllStream(
            es8Client, searchRequestBuilder, ElasticsearchUtil.MAP_CLASS);

    final var processInstanceId2IndexName =
        resStream.collect(Collectors.toMap(hit -> Long.valueOf(hit.id()), hit -> hit.index()));

    if (processInstanceId2IndexName.isEmpty()) {
      throw new NotFoundException(
          String.format("Process instances %s doesn't exists.", processInstanceIds));
    }
    return processInstanceId2IndexName;
  }
}
