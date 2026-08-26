/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import io.camunda.cluster.migration.MigrationStatusProvider;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.stereotype.Component;

/**
 * Reports whether this cluster has completed every condition required before the next minor-version
 * upgrade can safely be triggered (see camunda/product-hub#3067).
 *
 * <p>Collects every {@link MigrationStatusProvider} bean registered in this process. The response
 * only contains conditions for which a provider bean currently exists — {@code upgradeable} is
 * intentionally not meaningful until every planned provider (RDBMS schema, Elasticsearch/OpenSearch
 * schema, RocksDB snapshot, exporter flush) is registered; see {@link UpgradeReadinessResponse}.
 */
@Component
@WebEndpoint(id = "upgradeReadiness")
public class UpgradeReadinessEndpoint {

  private final MigrationStatusAggregator aggregator;

  @Autowired
  public UpgradeReadinessEndpoint(final List<MigrationStatusProvider> providers) {
    aggregator = new MigrationStatusAggregator(providers);
  }

  @ReadOperation(produces = "application/json")
  public UpgradeReadinessResponse getUpgradeReadiness() {
    return aggregator.aggregate();
  }
}
