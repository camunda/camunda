/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.opensearch.reader;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.schema.templates.JobTemplate;
import io.camunda.operate.webapp.reader.ListenerReader;
import io.camunda.operate.webapp.rest.dto.ListenerDto;
import io.camunda.operate.webapp.rest.dto.ListenerRequestDto;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchListenerReader extends OpensearchAbstractReader implements ListenerReader {

  @Autowired private JobTemplate jobTemplate;

  @Override
  public List<ListenerDto> getListenerExecutions(
      final String processInstanceId, final ListenerRequestDto request) {
    throw new UnsupportedOperationException("Listeners not implemented for Opensearch yet.");
  }
}
