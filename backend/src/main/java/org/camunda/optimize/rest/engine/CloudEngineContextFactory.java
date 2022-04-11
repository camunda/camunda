/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.engine;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.util.configuration.condition.CamundaCloudCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

@AllArgsConstructor
@Component
@Conditional(CamundaCloudCondition.class)
@Slf4j
public class CloudEngineContextFactory implements EngineContextFactory {

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
