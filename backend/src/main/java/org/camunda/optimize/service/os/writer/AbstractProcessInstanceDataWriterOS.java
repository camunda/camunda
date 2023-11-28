/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.writer;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.service.db.writer.AbstractProcessInstanceDataWriter;
import org.camunda.optimize.service.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.os.schema.OpenSearchSchemaManager;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Conditional;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static java.util.stream.Collectors.toSet;

@RequiredArgsConstructor
@Conditional(OpenSearchCondition.class)
public class AbstractProcessInstanceDataWriterOS<T extends OptimizeDto> implements AbstractProcessInstanceDataWriter<T> {

  protected final Logger log = LoggerFactory.getLogger(getClass());
  protected final OptimizeOpenSearchClient osClient;
  protected final OpenSearchSchemaManager openSearchSchemaManager;

  private final Set<String> existingInstanceIndexDefinitionKeys = ConcurrentHashMap.newKeySet();

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    existingInstanceIndexDefinitionKeys.clear();
  }

  @Override
  public void createInstanceIndicesIfMissing(final List<T> optimizeDtos,
                                             final Function<T, String> definitionKeyGetter) {
    createInstanceIndicesIfMissing(optimizeDtos.stream().map(definitionKeyGetter).collect(toSet()));
  }

  @Override
  public void createInstanceIndicesIfMissing(final Set<String> processDefinitionKeys) {
    //todo will be handled in the OPT-7376
  }

}
