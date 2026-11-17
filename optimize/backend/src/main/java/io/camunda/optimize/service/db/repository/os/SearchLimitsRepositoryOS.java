/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.os;

import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.repository.SearchLimitsRepository;
import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class SearchLimitsRepositoryOS implements SearchLimitsRepository {

  private final ConfigurationService configurationService;
  private final OptimizeOpenSearchClient osClient;

  public SearchLimitsRepositoryOS(
      final ConfigurationService configurationService, final OptimizeOpenSearchClient osClient) {
    this.configurationService = configurationService;
    this.osClient = osClient;
  }

  @Override
  public int aggregationBucketLimit() {
    // DatabaseSettings boxes this, so a value explicitly removed from the configuration arrives as
    // null. Substituting a default here would duplicate the one shipped in service-config.yaml and
    // silently diverge from it, while hiding a configuration that is broken for every aggregation
    // rather than just this caller — the group-by is sized from the same value.
    final Integer configured =
        configurationService.getOpenSearchConfiguration().getAggregationBucketLimit();
    if (configured == null) {
      throw new OptimizeConfigurationException(
          "opensearch.settings.aggregationBucketLimit is not configured. It is required to size "
              + "aggregations and to bound how many definitions a single search may group by.");
    }
    // Zero or negative is present but nonsensical rather than missing: clamp so a caller sizing
    // work against it cannot produce nothing or fail to make progress.
    return Math.max(1, configured);
  }

  @Override
  public String indexNamePrefix() {
    return osClient.getIndexNameService().getIndexPrefix();
  }
}
