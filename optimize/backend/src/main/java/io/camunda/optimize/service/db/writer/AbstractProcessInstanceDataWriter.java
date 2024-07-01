/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import io.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public interface AbstractProcessInstanceDataWriter<T extends OptimizeDto>
    extends ConfigurationReloadable {

  void createInstanceIndicesIfMissing(
      final List<T> optimizeDtos, final Function<T, String> definitionKeyGetter);

  void createInstanceIndicesIfMissing(final Set<String> processDefinitionKeys);
}
