/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.RequestOptionsProvider;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.client.RequestOptions;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.indices.GetAliasRequest;
import org.opensearch.client.opensearch.indices.GetAliasResponse;
import org.opensearch.client.opensearch.indices.RolloverRequest;
import org.opensearch.client.opensearch.indices.RolloverResponse;
import org.opensearch.client.opensearch.indices.rollover.RolloverConditions;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.db.DatabaseConstants.GB_UNIT;

@Slf4j
public class OptimizeOpenSearchClient extends DatabaseClient {

  @Getter
  private final OpenSearchClient openSearchClient;

  private RequestOptionsProvider requestOptionsProvider;

  @Getter
  private final RichOpenSearchClient richOpenSearchClient;

  public OptimizeOpenSearchClient(final OpenSearchClient openSearchClient,
                                  final OptimizeIndexNameService indexNameService) {
    this(openSearchClient, indexNameService, new RequestOptionsProvider());
  }

  public OptimizeOpenSearchClient(final OpenSearchClient openSearchClient,
                                  final OptimizeIndexNameService indexNameService,
                                  final RequestOptionsProvider requestOptionsProvider) {
    this.openSearchClient = openSearchClient;
    this.indexNameService = indexNameService;
    this.requestOptionsProvider = requestOptionsProvider;
    this.richOpenSearchClient = new RichOpenSearchClient(openSearchClient, indexNameService);
  }

  public final void close() {
    Optional.of(openSearchClient).ifPresent(OpenSearchClient::shutdown);
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

  @Override
  public Map<String, Set<String>> getAliasesForIndexPattern(final String indexNamePattern) throws IOException {
    final GetAliasResponse aliases = getAlias(indexNamePattern);
    return aliases.result().entrySet().stream()
      .collect(Collectors.toMap(
        Map.Entry::getKey,
        entry -> entry.getValue().aliases().keySet()
      ));
  }

  @Override
  public Set<String> getAllIndicesForAlias(final String aliasName) {
    GetAliasRequest aliasesRequest = new GetAliasRequest.Builder().name(aliasName).build();
    try {
      return openSearchClient
        .indices()
        .getAlias(aliasesRequest)
        .result()
        .keySet();
    } catch (Exception e) {
      String message = String.format("Could not retrieve index names for alias {%s}.", aliasName);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  public final GetAliasResponse getAlias(String indexNamePattern) throws IOException {
    final GetAliasRequest getAliasesRequest =
      new GetAliasRequest.Builder()
        .index(convertToPrefixedAliasName(indexNamePattern))
        .build();
    return openSearchClient.indices().getAlias(getAliasesRequest);
  }

  @Override
  public boolean triggerRollover(final String indexAliasName, final int maxIndexSizeGB) {
    RolloverRequest rolloverRequest =
      new RolloverRequest.Builder()
        .alias(indexAliasName)
        .conditions(new RolloverConditions.Builder().maxSize(maxIndexSizeGB + GB_UNIT).build())
        .build();

    log.info("Executing rollover request on {}", indexAliasName);
    try {
      RolloverResponse rolloverResponse = this.rollover(rolloverRequest);
      if (rolloverResponse.rolledOver()) {
        log.info(
          "Index with alias {} has been rolled over. New index name: {}",
          indexAliasName,
          rolloverResponse.newIndex()
        );
      } else {
        log.debug("Index with alias {} has not been rolled over. {}", indexAliasName,
                  rolloverConditionsStatus(rolloverResponse.conditions()));
      }
      return rolloverResponse.rolledOver();
    } catch (Exception e) {
      String message = "Failed to execute rollover request";
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  public final RolloverResponse rollover(RolloverRequest rolloverRequest) throws IOException {
    rolloverRequest = applyAliasPrefixAndRolloverConditions(rolloverRequest);
    return openSearchClient.indices().rollover(rolloverRequest);
  }

  private RolloverRequest applyAliasPrefixAndRolloverConditions(final RolloverRequest request) {
    return new RolloverRequest.Builder()
        .alias(indexNameService.getOptimizeIndexAliasForIndex(request.alias()))
        .conditions(request.conditions())
        .build();
  }

  private String rolloverConditionsStatus(Map<String, Boolean> conditions) {
    String conditionsNotMet = conditions.entrySet().stream()
      .filter(entry -> !entry.getValue())
      .map(entry -> "Condition " + entry.getKey() + " not met")
      .collect(Collectors.joining(", "));

    if (conditionsNotMet.isEmpty()) {
      return "Rollover not accomplished although all rollover conditions have been met.";
    } else {
      return conditionsNotMet;
    }
  }

}
