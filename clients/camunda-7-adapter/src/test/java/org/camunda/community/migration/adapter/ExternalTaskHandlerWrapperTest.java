/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.adapter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.common.io.Resources;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.client.impl.response.ActivatedJobImpl;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.camunda.community.migration.adapter.worker.ExternalTaskHandlerWrapper;
import org.junit.jupiter.api.Test;

public class ExternalTaskHandlerWrapperTest {

  private static String loadVariableContent() {
    try {
      return Resources.toString(
          Resources.getResource("test-variables.json"), Charset.defaultCharset());
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void shouldResolveVariables() {
    final JobClient client = mock(JobClient.class);
    final ActivatedJob job =
        new ActivatedJobImpl(
            new CamundaObjectMapper(),
            GatewayOuterClass.ActivatedJob.newBuilder()
                .setVariables(loadVariableContent())
                .setCustomHeaders("{}")
                .build());
    final ExternalTaskHandlerWrapper wrapper =
        new ExternalTaskHandlerWrapper(
            (externalTask, externalTaskService) -> {
              final String stringVar = externalTask.getVariable("stringVar");
              final TypedValue typedStringVar = externalTask.getVariableTyped("stringVar");
              assertThat(typedStringVar.getValue()).isEqualTo(stringVar);

              final Map<String, Object> objectVar = externalTask.getVariable("objectVar");
              final TypedValue typedObjectVar = externalTask.getVariableTyped("objectVar");
              assertThat(typedObjectVar.getValue()).isEqualTo(objectVar);

              final Number numberVar = externalTask.getVariable("numberVar");
              final TypedValue typedNumberVar = externalTask.getVariableTyped("numberVar");
              assertThat(typedNumberVar.getValue()).isEqualTo(numberVar);

              final Boolean booleanVar = externalTask.getVariable("booleanVar");
              final TypedValue typedBooleanVar = externalTask.getVariableTyped("booleanVar");
              assertThat(typedBooleanVar.getValue()).isEqualTo(booleanVar);

              final List<Object> listVar = externalTask.getVariable("listVar");
              final TypedValue typedListVar = externalTask.getVariableTyped("listVar");
              assertThat(typedListVar.getValue()).isEqualTo(listVar);

              final Object nullVar = externalTask.getVariable("nullVar");
              final TypedValue typedNullVar = externalTask.getVariableTyped("nullVar");
              assertThat(typedNullVar.getValue()).isEqualTo(nullVar);
              final String businessKey = externalTask.getBusinessKey();
              assertThat(businessKey).isNotNull().isEqualTo("12345");
            },
            Optional.of("businessKey"));
    wrapper.handle(client, job);
  }
}
