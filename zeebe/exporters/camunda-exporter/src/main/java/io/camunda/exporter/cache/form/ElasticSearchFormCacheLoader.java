/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.cache.form;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOptionsBuilders;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.core.search.SourceConfigBuilders;
import com.github.benmanes.caffeine.cache.CacheLoader;
import io.camunda.webapps.schema.descriptors.tasklist.index.FormIndex;
import io.camunda.webapps.schema.entities.form.FormEntity;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticSearchFormCacheLoader implements CacheLoader<String, CachedFormEntity> {

  private static final Logger LOG = LoggerFactory.getLogger(ElasticSearchFormCacheLoader.class);

  private final ElasticsearchClient client;
  private final String formIndexName;

  public ElasticSearchFormCacheLoader(
      final ElasticsearchClient client, final String formIndexName) {
    this.client = client;
    this.formIndexName = formIndexName;
  }

  @Override
  public CachedFormEntity load(final String formKey) throws IOException {
    final var termQuery = QueryBuilders.ids(i -> i.values(formKey));
    final var sorting =
        SortOptionsBuilders.field(f -> f.field(FormIndex.VERSION).order(SortOrder.Desc));
    final var sourceFilter =
        SourceConfigBuilders.filter()
            .includes(FormIndex.ID, FormIndex.BPMN_ID, FormIndex.VERSION)
            .build();
    final var response =
        client.search(
            request ->
                request
                    .index(formIndexName)
                    .query(termQuery)
                    .sort(sorting)
                    .source(s -> s.filter(sourceFilter))
                    .size(1),
            FormEntity.class);
    if (response.hits() != null && !response.hits().hits().isEmpty()) {
      final var formEntity = response.hits().hits().getFirst().source();
      return new CachedFormEntity(formEntity.getFormId(), formEntity.getVersion());
    } else {
      LOG.debug("Form '{}' not found in Elasticsearch", formKey);
      return null;
    }
  }
}
