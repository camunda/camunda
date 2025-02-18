/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.spring.client.jobhandling.parameter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.impl.ZeebeObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VariableResolverTest {

  private VariableResolver resolver;
  @Mock private JobClient jobClient;
  @Mock private ActivatedJob job;

  @BeforeEach
  void setUp() {
    resolver = new VariableResolver("testVar", String.class, new ZeebeObjectMapper());
  }

  @Test
  void shouldResolveVariableNotPresent() {
    when(job.getVariablesAsMap()).thenReturn(Map.of("anotherVar", "another value"));

    final Object resolvedValue = resolver.resolve(jobClient, job);

    assertNull(resolvedValue);
  }

  @Test
  void shouldResolveVariableIsPresent() {
    when(job.getVariablesAsMap()).thenReturn(Map.of("testVar", "test value"));

    final Object resolvedValue = resolver.resolve(jobClient, job);

    assertEquals("test value", resolvedValue);
  }
}
