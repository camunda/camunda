/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize;

import org.camunda.optimize.test.engine.AuthorizationClient;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtension;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtension;
import org.camunda.optimize.test.optimize.AlertClient;
import org.camunda.optimize.test.optimize.CollectionClient;
import org.camunda.optimize.test.optimize.DashboardClient;
import org.camunda.optimize.test.optimize.EventClient;
import org.camunda.optimize.test.optimize.EventProcessClient;
import org.camunda.optimize.test.optimize.ReportClient;
import org.camunda.optimize.test.optimize.SharingClient;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.function.Supplier;

public abstract class AbstractIT {

  @RegisterExtension
  @Order(1)
  public ElasticSearchIntegrationTestExtension elasticSearchIntegrationTestExtension
    = new ElasticSearchIntegrationTestExtension();
  @RegisterExtension
  @Order(2)
  public EngineIntegrationExtension engineIntegrationExtension = new EngineIntegrationExtension();
  @RegisterExtension
  @Order(3)
  public EmbeddedOptimizeExtension embeddedOptimizeExtension = new EmbeddedOptimizeExtension();
  private final Supplier<OptimizeRequestExecutor> optimizeRequestExecutorSupplier =
    () -> embeddedOptimizeExtension.getRequestExecutor();

  // engine test helpers
  protected AuthorizationClient authorizationClient = new AuthorizationClient(engineIntegrationExtension);

  // optimize test helpers
  protected CollectionClient collectionClient = new CollectionClient(optimizeRequestExecutorSupplier);
  protected ReportClient reportClient = new ReportClient(optimizeRequestExecutorSupplier);
  protected AlertClient alertClient = new AlertClient(optimizeRequestExecutorSupplier);
  protected DashboardClient dashboardClient = new DashboardClient(optimizeRequestExecutorSupplier);
  protected EventProcessClient eventProcessClient = new EventProcessClient(optimizeRequestExecutorSupplier);
  protected SharingClient sharingClient = new SharingClient(optimizeRequestExecutorSupplier);
  protected EventClient eventClient = new EventClient(embeddedOptimizeExtension, elasticSearchIntegrationTestExtension);
}
