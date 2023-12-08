/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.writer.variable;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableDto;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.os.schema.OpenSearchSchemaManager;
import org.camunda.optimize.service.db.os.writer.AbstractProcessInstanceDataWriterOS;
import org.camunda.optimize.service.db.writer.variable.ProcessVariableUpdateWriter;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class ProcessVariableUpdateWriterOS extends AbstractProcessInstanceDataWriterOS<ProcessVariableDto>
  implements ProcessVariableUpdateWriter {

  public ProcessVariableUpdateWriterOS(final OptimizeOpenSearchClient osClient, final OpenSearchSchemaManager openSearchSchemaManager) {
    super(osClient, openSearchSchemaManager);
  }

  @Override
  public List<ImportRequestDto> generateVariableUpdateImports(final List<ProcessVariableDto> variables) {
    //todo will be handled in the OPT-7376
    return new ArrayList<>();
  }

  @Override
  public void deleteVariableDataByProcessInstanceIds(final String processDefinitionKey, final List<String> processInstanceIds) {
    //todo will be handled in the OPT-7376
  }

}
