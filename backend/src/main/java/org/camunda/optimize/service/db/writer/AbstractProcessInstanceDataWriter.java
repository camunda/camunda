/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer;

import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

public interface AbstractProcessInstanceDataWriter<T extends OptimizeDto> extends ConfigurationReloadable {

  void createInstanceIndicesIfMissing(final List<T> optimizeDtos,
                                      final Function<T, String> definitionKeyGetter);

  void createInstanceIndicesIfMissing(final Set<String> processDefinitionKeys);

}
