/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.webapp.reader.ListenerReader;
import io.camunda.operate.webapp.rest.dto.ListenerDto;
import io.camunda.operate.webapp.rest.dto.ListenerRequestDto;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchListenerReader extends AbstractReader implements ListenerReader {

  @Autowired private ProcessInstanceReader processInstanceReader;

  @Override
  public List<ListenerDto> getListenerExecutions(
      final String processInstanceId, final ListenerRequestDto request) {
    throw new UnsupportedOperationException("Listeners not implemented for Elasticsearch yet.");
  }
}
