/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer;

import org.camunda.optimize.dto.optimize.importing.LastKpiEvaluationResultsDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessUpdateDto;

import java.util.Map;

public interface ProcessOverviewWriter {

  void updateProcessConfiguration(final String processDefinitionKey, final ProcessUpdateDto processUpdateDto);

  void updateProcessDigestResults(final String processDefKey, final ProcessDigestDto processDigestDto);

  void updateProcessOwnerIfNotSet(final String processDefinitionKey, final String ownerId);

  void updateKpisForProcessDefinitions(Map<String, LastKpiEvaluationResultsDto> definitionKeyToKpis);

  void deleteProcessOwnerEntry(final String processDefinitionKey);

}
