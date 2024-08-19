/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.engine;

import io.camunda.optimize.service.util.configuration.condition.CamundaCloudCondition;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(CamundaCloudCondition.class)
public class CloudEngineContextFactory implements EngineContextFactory {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(CloudEngineContextFactory.class);

  public CloudEngineContextFactory() {}

  @Override
  @PostConstruct
  public void init() {
    // noop
  }

  @Override
  @PreDestroy
  public void close() {
    // noop
  }

  @Override
  public Optional<EngineContext> getConfiguredEngineByAlias(final String engineAlias) {
    return Optional.empty();
  }

  @Override
  public Collection<EngineContext> getConfiguredEngines() {
    return Collections.emptyList();
  }
}
