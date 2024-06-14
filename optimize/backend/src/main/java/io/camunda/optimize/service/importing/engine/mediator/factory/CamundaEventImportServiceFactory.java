/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.importing.engine.mediator.factory;

import io.camunda.optimize.rest.engine.EngineContext;
import io.camunda.optimize.service.CamundaEventImportService;
import io.camunda.optimize.service.db.writer.BusinessKeyWriter;
import io.camunda.optimize.service.db.writer.CamundaActivityEventWriter;
import io.camunda.optimize.service.db.writer.variable.VariableUpdateInstanceWriter;
import io.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionResolverService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
public class CamundaEventImportServiceFactory {

  private final VariableUpdateInstanceWriter variableUpdateInstanceWriter;
  private final CamundaActivityEventWriter camundaActivityEventWriter;
  private final BusinessKeyWriter businessKeyWriter;
  private final ProcessDefinitionResolverService processDefinitionResolverService;
  private final ConfigurationService configurationService;

  public CamundaEventImportService createCamundaEventService(final EngineContext engineContext) {
    return new CamundaEventImportService(
        variableUpdateInstanceWriter,
        camundaActivityEventWriter,
        businessKeyWriter,
        processDefinitionResolverService,
        configurationService,
        engineContext);
  }
}
