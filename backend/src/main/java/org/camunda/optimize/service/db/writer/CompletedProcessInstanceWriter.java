/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer;

import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;

import java.util.List;
import java.util.Set;

import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.BUSINESS_KEY;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.DATA_SOURCE;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.DURATION;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.END_DATE;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_ID;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_VERSION;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.START_DATE;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.STATE;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.TENANT_ID;

public interface CompletedProcessInstanceWriter {

  Set<String> UPDATABLE_FIELDS = Set.of(
    PROCESS_DEFINITION_KEY, PROCESS_DEFINITION_VERSION, PROCESS_DEFINITION_ID,
    BUSINESS_KEY, START_DATE, END_DATE, DURATION, STATE,
    DATA_SOURCE, TENANT_ID
  );

  List<ImportRequestDto> generateProcessInstanceImports(List<ProcessInstanceDto> processInstances);

  void deleteByIds(final String definitionKey,
                   final List<String> processInstanceIds);

}
