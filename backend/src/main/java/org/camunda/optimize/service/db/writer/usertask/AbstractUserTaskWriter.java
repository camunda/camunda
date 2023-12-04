/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer.usertask;

import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.service.db.DatabaseClient;

import java.util.List;

public interface AbstractUserTaskWriter {

  List<ImportRequestDto> generateUserTaskImports(final String importItemName,
                                                 final DatabaseClient databaseClient,
                                                 final List<FlowNodeInstanceDto> userTaskInstances);

}
