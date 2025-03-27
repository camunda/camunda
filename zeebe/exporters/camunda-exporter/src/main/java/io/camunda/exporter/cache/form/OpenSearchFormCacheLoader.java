/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.cache.form;

import com.github.benmanes.caffeine.cache.CacheLoader;
import io.camunda.webapps.schema.descriptors.index.FormIndex;
import io.camunda.webapps.schema.entities.form.FormEntity;
import java.io.IOException;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.SortOptionsBuilders;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch.core.search.SourceConfigBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenSearchFormCacheLoader implements CacheLoader<String, CachedFormEntity> {
  private static final Logger LOG = LoggerFactory.getLogger(OpenSearchFormCacheLoader.class);

  private final OpenSearchClient client;
  private final String formIndexName;

  public OpenSearchFormCacheLoader(final OpenSearchClient client, final String formIndexName) {
    this.client = client;
    this.formIndexName = formIndexName;
  }

  @Override
  public CachedFormEntity load(final String formKey) throws IOException {
    final var idQuery = QueryBuilders.ids().values(formKey).build();
    final var sorting =
        SortOptionsBuilders.field().field(FormIndex.VERSION).order(SortOrder.Desc).build();
    final var sourceFilter =
        SourceConfigBuilders.filter()
            .includes(FormIndex.ID, FormIndex.BPMN_ID, FormIndex.VERSION)
            .build();
    final var response =
        client.search(
            request ->
                request
                    .index(formIndexName)
                    .query(q -> q.ids(idQuery))
                    .sort(s -> s.field(sorting))
                    .source(s -> s.filter(sourceFilter))
                    .size(1),
            FormEntity.class);
    if (response.hits() != null && !response.hits().hits().isEmpty()) {
      final var formEntity = response.hits().hits().getFirst().source();
      return new CachedFormEntity(formEntity.getFormId(), formEntity.getVersion());
    } else {
      LOG.debug("Form '{}' not found in OpenSearch", formKey);
      return null;
    }
  }
}
