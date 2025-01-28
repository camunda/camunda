/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.adapter.externalTask;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import io.camunda.client.api.response.ActivatedJob;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.ThrowingConsumer;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class JobWrappingExternalTaskTest {
  private static final long ZEEBE_TEST_KEY = 12345L;
  private static final String ZEEBE_TEST_KEY_STRING = String.valueOf(ZEEBE_TEST_KEY);
  private static final String TEST_ID = "abc-123-xyz";
  private static final String BUSINESS_KEY_VAR_NAME = "businessKey";
  @Mock ActivatedJob job;

  JobWrappingExternalTask externalTask;

  private static Function<Entry<String, Object>, String> displayNameGenerator() {
    return Entry::getKey;
  }

  private static Stream<Entry<String, Object>> inputStream() {
    return variables().entrySet().stream();
  }

  private static Map<String, Object> variables() {
    final ObjectMapper objectMapper = new ObjectMapper();
    try {
      return objectMapper.readValue(
          Resources.getResource("test-variables.json"),
          new TypeReference<Map<String, Object>>() {});
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static Stream<DynamicTest> dynamicTest(
      final ThrowingConsumer<Entry<String, Object>> testFunction) {
    return DynamicTest.stream(inputStream(), displayNameGenerator(), testFunction);
  }

  @BeforeEach
  public void setup() {
    if (externalTask == null) {
      externalTask = new JobWrappingExternalTask(job, Optional.of(BUSINESS_KEY_VAR_NAME));
    }
  }

  // TODO are these all operations?
  @Test
  public void testActivityId() {
    when(job.getElementId()).thenReturn(TEST_ID);
    final String activityId = externalTask.getActivityId();
    assertThat(activityId).isEqualTo(TEST_ID);
    verify(job, times(1)).getElementId();
  }

  @Test
  public void testActivityInstanceId() {
    when(job.getElementInstanceKey()).thenReturn(ZEEBE_TEST_KEY);
    final String activityInstanceId = externalTask.getActivityInstanceId();
    assertThat(activityInstanceId).isEqualTo(ZEEBE_TEST_KEY_STRING);
    verify(job, times(1)).getElementInstanceKey();
  }

  @Test
  public void testErrorMessage() {
    assertThrows(UnsupportedOperationException.class, () -> externalTask.getErrorMessage());
  }

  @Test
  public void testErrorDetails() {
    assertThrows(UnsupportedOperationException.class, () -> externalTask.getErrorDetails());
  }

  @Test
  public void testExecutionId() {
    assertThrows(UnsupportedOperationException.class, () -> externalTask.getExecutionId());
  }

  @Test
  public void testId() {
    when(job.getKey()).thenReturn(ZEEBE_TEST_KEY);
    final String id = externalTask.getId();
    assertThat(id).isEqualTo(ZEEBE_TEST_KEY_STRING);
    verify(job, times(1)).getKey();
  }

  @Test
  public void testLockExpirationTime() {
    final long currentTimeMillis = System.currentTimeMillis();
    when(job.getDeadline()).thenReturn(currentTimeMillis);
    final Date lockExpirationTime = externalTask.getLockExpirationTime();
    assertThat(lockExpirationTime).isEqualTo(Instant.ofEpochMilli(currentTimeMillis));
    verify(job, times(1)).getDeadline();
  }

  @Test
  public void testProcessDefinitionId() {
    when(job.getProcessDefinitionKey()).thenReturn(ZEEBE_TEST_KEY);
    final String processDefinitionId = externalTask.getProcessDefinitionId();
    assertThat(processDefinitionId).isEqualTo(ZEEBE_TEST_KEY_STRING);
    verify(job, times(1)).getProcessDefinitionKey();
  }

  @Test
  public void testProcessDefinitionKey() {
    when(job.getBpmnProcessId()).thenReturn(TEST_ID);
    final String processDefinitionKey = externalTask.getProcessDefinitionKey();
    assertThat(processDefinitionKey).isEqualTo(TEST_ID);
    verify(job, times(1)).getBpmnProcessId();
  }

  @Test
  public void testProcessDefinitionVersionTag() {
    assertThrows(
        UnsupportedOperationException.class, () -> externalTask.getProcessDefinitionVersionTag());
  }

  @Test
  public void testProcessInstanceId() {
    when(job.getProcessInstanceKey()).thenReturn(ZEEBE_TEST_KEY);
    final String processInstanceId = externalTask.getProcessInstanceId();
    assertThat(processInstanceId).isEqualTo(ZEEBE_TEST_KEY_STRING);
    verify(job, times(1)).getProcessInstanceKey();
  }

  @Test
  public void testRetries() {
    when(job.getRetries()).thenReturn(3);
    final Integer retries = externalTask.getRetries();
    assertThat(retries).isEqualTo(3);
    verify(job, times(1)).getRetries();
  }

  @Test
  public void testWorkerId() {
    when(job.getWorker()).thenReturn(TEST_ID);
    final String workerId = externalTask.getWorkerId();
    assertThat(workerId).isEqualTo(TEST_ID);
    verify(job, times(1)).getWorker();
  }

  @Test
  public void testTopicName() {
    when(job.getType()).thenReturn(TEST_ID);
    final String topicName = externalTask.getTopicName();
    assertThat(topicName).isEqualTo(TEST_ID);
    verify(job, times(1)).getType();
  }

  @Test
  public void testTenantId() {
    when(job.getTenantId()).thenReturn(TEST_ID);
    final String tenantId = externalTask.getTenantId();
    assertThat(tenantId).isEqualTo(TEST_ID);
    verify(job, times(1)).getTenantId();
  }

  @Test
  public void getPriority() {
    assertThrows(UnsupportedOperationException.class, () -> externalTask.getPriority());
  }

  @TestFactory
  public Stream<DynamicTest> testVariable() {
    when(job.getVariablesAsMap()).thenReturn(variables());
    return dynamicTest(
        e -> {
          final Object variable = externalTask.getVariable(e.getKey());
          assertThat(variable).isEqualTo(e.getValue());
        });
  }

  @TestFactory
  public Stream<DynamicTest> testVariableTyped() {
    when(job.getVariablesAsMap()).thenReturn(variables());
    return dynamicTest(
        e -> {
          final TypedValue variable = externalTask.getVariableTyped(e.getKey());
          assertThat(variable.getValue()).isEqualTo(e.getValue());
        });
  }

  @Test
  public void testAllVariables() {
    when(job.getVariablesAsMap()).thenReturn(variables());
    final Map<String, Object> allVariables = externalTask.getAllVariables();
    assertThat(allVariables).isEqualTo(variables());
  }

  @Test
  public void testAllVariablesTyped() {
    when(job.getVariablesAsMap()).thenReturn(variables());
    final VariableMap allVariablesTyped = externalTask.getAllVariablesTyped();
    assertThat(allVariablesTyped).isEqualTo(variables());
  }

  @Test
  public void testBusinessKey() {
    when(job.getVariablesAsMap()).thenReturn(variables());
    final String businessKey = externalTask.getBusinessKey();
    assertThat(businessKey).isEqualTo(variables().get(BUSINESS_KEY_VAR_NAME));
  }

  @Test
  public void testExtensionProperty() {
    when(job.getCustomHeaders()).thenReturn(Collections.singletonMap("key", "value"));
    final String extensionProperty = externalTask.getExtensionProperty("key");
    assertThat(extensionProperty).isEqualTo("value");
  }

  @Test
  public void testExtensionProperties() {
    when(job.getCustomHeaders()).thenReturn(Collections.singletonMap("key", "value"));
    final Map<String, String> extensionProperties = externalTask.getExtensionProperties();
    assertThat(extensionProperties).hasFieldOrProperty("key").hasSize(1).containsValue("value");
  }
}
