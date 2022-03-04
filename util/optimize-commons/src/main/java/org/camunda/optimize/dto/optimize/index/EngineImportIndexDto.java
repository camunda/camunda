/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.index;

import org.camunda.optimize.dto.optimize.OptimizeDto;

public interface EngineImportIndexDto extends OptimizeDto {
  String getEngine();
  String getEsTypeIndexRefersTo();
}
