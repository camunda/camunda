/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.reader;

import org.camunda.optimize.dto.optimize.query.variable.VariableUpdateInstanceDto;

import java.util.List;
import java.util.Set;

public interface VariableUpdateInstanceReader {

  List<VariableUpdateInstanceDto> getVariableInstanceUpdatesForProcessInstanceIds(Set<String> processInstanceIds);

}
