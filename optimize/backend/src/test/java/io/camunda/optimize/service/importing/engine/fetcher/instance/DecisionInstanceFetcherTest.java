/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.fetcher.instance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.rest.engine.EngineContext;
import io.camunda.optimize.service.importing.page.TimestampBasedImportPage;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import io.camunda.optimize.service.util.importing.EngineConstants;
import io.camunda.optimize.service.util.mapper.OptimizeDateTimeFormatterFactory;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.GenericType;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DecisionInstanceFetcherTest {

  private final ConfigurationService configurationService =
      ConfigurationServiceBuilder.createDefaultConfiguration();

  @Mock private EngineContext engineContext;
  @Mock private BackoffCalculator backoffCalculator;

  @Mock(answer = Answers.RETURNS_SELF)
  private Client engineClient;

  @Mock(answer = Answers.RETURNS_SELF)
  private WebTarget target;

  @Mock(answer = Answers.RETURNS_SELF)
  private Invocation.Builder requestBuilder;

  private DecisionInstanceFetcher underTest;

  @BeforeEach
  public void before() {
    underTest = new DecisionInstanceFetcher(engineContext);
    underTest.setBackoffCalculator(backoffCalculator);
    underTest.setConfigurationService(configurationService);
    underTest.setDateTimeFormatter(new OptimizeDateTimeFormatterFactory().getObject());

    when(engineContext.getEngineClient()).thenReturn(engineClient);
    when(engineContext.getEngineAlias())
        .thenReturn(configurationService.getConfiguredEngines().keySet().iterator().next());
    when(engineClient.target(anyString())).thenReturn(target);
    when(target.request(anyString())).thenReturn(requestBuilder);
    when(requestBuilder.get(any(GenericType.class))).thenReturn(new ArrayList<>());
  }

  @Test
  public void testFetchHistoricInstancePageUsesMaxPageSize() {
    final int maxPageSize = 2233;
    configurationService.setEngineImportDecisionInstanceMaxPageSize(maxPageSize);

    underTest.fetchHistoricDecisionInstances(new TimestampBasedImportPage());

    verify(target, times(1))
        .queryParam(eq(EngineConstants.MAX_RESULTS_TO_RETURN), eq((long) maxPageSize));
  }
}
