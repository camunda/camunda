/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.RequestOptionsProvider;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.elasticsearch.client.RequestOptions;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Conditional;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Conditional(OpenSearchCondition.class)
public class OptimizeOpenSearchClient extends DatabaseClient {

  @Getter
  private final OpenSearchClient databaseClient;

  private RequestOptionsProvider requestOptionsProvider;

  @Getter
  private final RichOpenSearchClient richOpenSearchClient;

  public OptimizeOpenSearchClient(final OpenSearchClient databaseClient,
                                  final OptimizeIndexNameService indexNameService) {
    this(databaseClient, indexNameService, new RequestOptionsProvider());
  }

  public OptimizeOpenSearchClient(final OpenSearchClient databaseClient,
                                  final OptimizeIndexNameService indexNameService,
                                  final RequestOptionsProvider requestOptionsProvider) {
    this.databaseClient = databaseClient;
    this.indexNameService = indexNameService;
    this.requestOptionsProvider = requestOptionsProvider;
    this.richOpenSearchClient = new RichOpenSearchClient(databaseClient, indexNameService);
  }

  public final void close() {
    Optional.of(databaseClient).ifPresent(OpenSearchClient::shutdown);
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    final ConfigurationService configurationService = context.getBean(ConfigurationService.class);
    this.indexNameService = context.getBean(OptimizeIndexNameService.class);
    // For now we are descoping the custom header provider, to be evaluated with OPT-7400
    this.requestOptionsProvider = new RequestOptionsProvider(List.of(), configurationService);
  }

  public RequestOptions requestOptions() {
    return requestOptionsProvider.getRequestOptions();
  }

  public Map<String, String> getIndexSettings(String... fields) throws IOException {
    return getRichOpenSearchClient().index().getIndexSettingsWithRetries(
      getIndexNameService().getIndexPrefix() + "*",
      fields
    );
  }

  @Override
  public Map<String, Set<String>> getAliasesForIndex(final String indexName) throws IOException {
    throw new NotImplementedException("Feature not yet available"); // TODO will be implemented with OPT-7229
  }

  @Override
  public Set<String> getAllIndicesForAlias(final String aliasName) throws IOException {
    throw new NotImplementedException("Feature not yet available"); // TODO will be implemented with OPT-7229
  }

  @Override
  public boolean triggerRollover(final String indexAliasName, final int maxIndexSizeGB) {
    throw new NotImplementedException("Feature not yet available"); // TODO will be implemented with OPT-7229
  }
}
