/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.fetcher.instance;

import org.camunda.optimize.dto.engine.DecisionDefinitionXmlEngineDto;
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

import static org.camunda.optimize.service.util.importing.EngineConstants.DECISION_DEFINITION_XML_ENDPOINT_TEMPLATE;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionDefinitionXmlFetcher extends RetryBackoffEngineEntityFetcher<DecisionDefinitionXmlEngineDto> {

  public DecisionDefinitionXmlFetcher(final EngineContext engineContext) {
    super(engineContext);
  }

  public List<DecisionDefinitionXmlEngineDto> fetchXmlsForDefinitions(IdSetBasedImportPage page) {
    final Set<String> ids = page.getIds();
    return fetchXmlsForDefinitions(new ArrayList<>(ids));
  }

  private List<DecisionDefinitionXmlEngineDto> fetchXmlsForDefinitions(final List<String> decisionDefinitionIds) {
    logger.debug("Fetching decision definition xml ...");
    final List<DecisionDefinitionXmlEngineDto> xmls = new ArrayList<>(decisionDefinitionIds.size());
    final long requestStart = System.currentTimeMillis();
    for (String processDefinitionId : decisionDefinitionIds) {
      final List<DecisionDefinitionXmlEngineDto> singleXml = fetchWithRetryIgnoreClientError(
        () -> performGetDecisionDefinitionXmlRequest(processDefinitionId)
      );
      xmls.addAll(singleXml);
    }
    final long requestEnd = System.currentTimeMillis();
    logger.debug(
      "Fetched [{}] decision definition xmls within [{}] ms", decisionDefinitionIds.size(), requestEnd - requestStart
    );
    return xmls;
  }

  private List<DecisionDefinitionXmlEngineDto> performGetDecisionDefinitionXmlRequest(final String decisionDefinitionId) {
    final DecisionDefinitionXmlEngineDto decisionDefinitionXmlEngineDto = getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(DECISION_DEFINITION_XML_ENDPOINT_TEMPLATE)
      .resolveTemplate("id", decisionDefinitionId)
      .request(MediaType.APPLICATION_JSON)
      .get(DecisionDefinitionXmlEngineDto.class);
    return Collections.singletonList(decisionDefinitionXmlEngineDto);
  }
}
