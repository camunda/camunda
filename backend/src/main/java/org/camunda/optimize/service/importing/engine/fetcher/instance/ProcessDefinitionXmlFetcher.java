/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.fetcher.instance;

import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.importing.page.IdSetBasedImportPage;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.camunda.optimize.service.util.importing.EngineConstants.PROCESS_DEFINITION_XML_ENDPOINT_TEMPLATE;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDefinitionXmlFetcher extends RetryBackoffEngineEntityFetcher<ProcessDefinitionXmlEngineDto> {

  public ProcessDefinitionXmlFetcher(EngineContext engineContext) {
    super(engineContext);
  }

  public List<ProcessDefinitionXmlEngineDto> fetchXmlsForDefinitions(IdSetBasedImportPage page) {
    Set<String> ids = page.getIds();
    return fetchXmlsForDefinitions(new ArrayList<>(ids));
  }

  private List<ProcessDefinitionXmlEngineDto> fetchXmlsForDefinitions(List<String> processDefinitionIds) {
    List<ProcessDefinitionXmlEngineDto> xmls = new ArrayList<>(processDefinitionIds.size());
    logger.debug("Fetching process definition xml ...");
    long requestStart = System.currentTimeMillis();
    for (String processDefinitionId : processDefinitionIds) {
      List<ProcessDefinitionXmlEngineDto> singleXml =
        fetchWithRetryIgnoreClientError(() -> performProcessDefinitionXmlRequest(processDefinitionId));
      xmls.addAll(singleXml);
    }
    long requestEnd = System.currentTimeMillis();
    logger.debug(
      "Fetched [{}] process definition xmls within [{}] ms",
      processDefinitionIds.size(),
      requestEnd - requestStart
    );
    return xmls;
  }

  private List<ProcessDefinitionXmlEngineDto> performProcessDefinitionXmlRequest(String processDefinitionId) {
    ProcessDefinitionXmlEngineDto processDefinitionXmlEngineDto = getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(PROCESS_DEFINITION_XML_ENDPOINT_TEMPLATE)
      .resolveTemplate("id", processDefinitionId)
      .request(MediaType.APPLICATION_JSON)
      .get(ProcessDefinitionXmlEngineDto.class);
    return Collections.singletonList(processDefinitionXmlEngineDto);
  }
}
