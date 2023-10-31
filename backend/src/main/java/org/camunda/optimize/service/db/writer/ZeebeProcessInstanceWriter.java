/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer;

import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;

import java.util.List;

public interface ZeebeProcessInstanceWriter {

  String NEW_INSTANCE = "instance";
  String FORMATTER = "dateFormatPattern";

  List<ImportRequestDto> generateProcessInstanceImports(List<ProcessInstanceDto> processInstances);

}
