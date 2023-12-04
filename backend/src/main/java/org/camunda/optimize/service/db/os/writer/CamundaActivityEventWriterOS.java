/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.writer;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.query.event.process.CamundaActivityEventDto;
import org.camunda.optimize.service.db.writer.CamundaActivityEventWriter;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class CamundaActivityEventWriterOS implements CamundaActivityEventWriter {

  @Override
  public List<ImportRequestDto> generateImportRequests(final List<CamundaActivityEventDto> camundaActivityEvents) {
    return null;
  }

  @Override
  public void deleteByProcessInstanceIds(final String definitionKey, final List<String> processInstanceIds) {
    //todo will be handled in the OPT-7376
  }

}
