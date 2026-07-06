/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import com.google.common.collect.Iterators;
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ProcessDefinitionReader {

  default Iterator<ProcessDefinitionOptimizeDto> getAllProcessDefinitions() {
    final Iterator<List<ProcessDefinitionOptimizeDto>> definitionsPageIterator =
        getDefinitionReader().getDefinitionsIterator(DefinitionType.PROCESS, false, false, true);
    final Iterator<Iterator<ProcessDefinitionOptimizeDto>> nestedIterator =
        Iterators.transform(definitionsPageIterator, List::iterator);
    return Iterators.concat(nestedIterator);
  }

  default String getLatestVersionToKey(final String key) {
    return getDefinitionReader().getLatestVersionToKey(DefinitionType.PROCESS, key);
  }

  Optional<ProcessDefinitionOptimizeDto> getProcessDefinition(final String definitionId);

  Set<String> getAllNonOnboardedProcessDefinitionKeys();

  DefinitionReader getDefinitionReader();
}
