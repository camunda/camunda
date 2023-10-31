/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer;

import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.query.event.process.CamundaActivityEventDto;

import java.util.List;

public interface CamundaActivityEventWriter {

   List<ImportRequestDto> generateImportRequests(List<CamundaActivityEventDto> camundaActivityEvents);

   void deleteByProcessInstanceIds(final String definitionKey, final List<String> processInstanceIds);

}
