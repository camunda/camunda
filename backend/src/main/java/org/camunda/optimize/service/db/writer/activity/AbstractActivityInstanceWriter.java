/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer.activity;

import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;

import java.util.List;

public interface AbstractActivityInstanceWriter {

  List<ImportRequestDto> generateActivityInstanceImports(List<FlowNodeEventDto> activityInstances);

  FlowNodeInstanceDto fromActivityInstance(final FlowNodeEventDto activityInstance);

}
