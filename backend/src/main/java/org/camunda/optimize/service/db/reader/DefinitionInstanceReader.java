/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.reader;

import org.camunda.optimize.dto.optimize.DefinitionType;

import java.util.Set;

public interface DefinitionInstanceReader {

  Set<String> getAllExistingDefinitionKeys(final DefinitionType type);

  Set<String> getAllExistingDefinitionKeys(final DefinitionType type,
                                                  final Set<String> instanceIds);
}
