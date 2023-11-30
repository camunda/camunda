/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.writer;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.importing.LastKpiEvaluationResultsDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessUpdateDto;
import org.camunda.optimize.service.db.writer.ProcessOverviewWriter;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Map;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class ProcessOverviewWriterOS implements ProcessOverviewWriter {

  @Override
  public void updateProcessConfiguration(final String processDefinitionKey, final ProcessUpdateDto processUpdateDto) {
    //todo will be handled in the OPT-7376
  }

  @Override
  public void updateProcessDigestResults(final String processDefKey, final ProcessDigestDto processDigestDto) {
    //todo will be handled in the OPT-7376
  }

  @Override
  public void updateProcessOwnerIfNotSet(final String processDefinitionKey, final String ownerId) {
    //todo will be handled in the OPT-7376
  }

  @Override
  public void updateKpisForProcessDefinitions(final Map<String, LastKpiEvaluationResultsDto> definitionKeyToKpis) {
    //todo will be handled in the OPT-7376
  }

  @Override
  public void deleteProcessOwnerEntry(final String processDefinitionKey) {
    //todo will be handled in the OPT-7376
  }

}
