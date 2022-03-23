/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.event.handler;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.service.importing.EngineImportIndexHandler;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class EventImportIndexHandlerRegistry {

  private final BeanFactory beanFactory;

  private final Map<String, CamundaEventTraceImportIndexHandler> camundaEventTraceHandlers = new HashMap<>();

  public List<EngineImportIndexHandler<?, ?>> getAllHandlers() {
    final List<EngineImportIndexHandler<?, ?>> result = new ArrayList<>();
    result.add(getExternalEventTraceImportIndexHandler());
    result.addAll(camundaEventTraceHandlers.values());
    return result;
  }

  public ExternalEventTraceImportIndexHandler getExternalEventTraceImportIndexHandler() {
    return beanFactory.getBean(ExternalEventTraceImportIndexHandler.class);
  }

  public synchronized CamundaEventTraceImportIndexHandler getCamundaEventTraceImportIndexHandler(final String definitionKey) {
    return camundaEventTraceHandlers.computeIfAbsent(
      definitionKey,
      key -> beanFactory.getBean(CamundaEventTraceImportIndexHandler.class, key)
    );
  }

}
