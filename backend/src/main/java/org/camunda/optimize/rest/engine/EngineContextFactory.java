/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.engine;

import java.util.Collection;
import java.util.Optional;

public interface EngineContextFactory {
  void init();

  void close();

  Optional<EngineContext> getConfiguredEngineByAlias(String engineAlias);

  Collection<EngineContext> getConfiguredEngines();
}
