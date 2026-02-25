/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.conditions;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.camunda.configuration.conditions.ConditionalOnWebappEnabled.OnWebappEnabledCondition;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class ConditionalOnWebappEnabledTest {

  private static final String TEST_WEBAPP_NAME = "testwebapp";
  private static final String LEGACY_PROPERTY = "camunda." + TEST_WEBAPP_NAME + ".webapp-enabled";
  private static final String UNIFIED_PROPERTY = "camunda.webapps." + TEST_WEBAPP_NAME + ".enabled";

  private OnWebappEnabledCondition condition;
  private ConditionContext context;
  private Environment environment;
  private AnnotatedTypeMetadata metadata;

  @BeforeEach
  void setup() {
    condition = new OnWebappEnabledCondition();
    context = mock(ConditionContext.class);
    environment = mock(Environment.class);
    metadata = mock(AnnotatedTypeMetadata.class);

    when(context.getEnvironment()).thenReturn(environment);
    when(metadata.getAnnotationAttributes(condition.getConditionalClassName()))
        .thenReturn(Map.of("value", new String[] {TEST_WEBAPP_NAME}));
  }

  @Test
  void testShouldMatchwhenAllPropertiesAreTrue() {
    // given
    when(environment.getProperty(LEGACY_PROPERTY, Boolean.class, true)).thenReturn(true);
    when(environment.getProperty(UNIFIED_PROPERTY, Boolean.class, true)).thenReturn(true);

    // when
    final boolean result = condition.matches(context, metadata);

    // then
    assertThat(result).isTrue();
  }

  @Test
  void testShouldNotMatchWhenLegacyPropertyIsFalse() {
    // given
    when(environment.getProperty(UNIFIED_PROPERTY, Boolean.class, true)).thenReturn(true);
    when(environment.getProperty(LEGACY_PROPERTY, Boolean.class, true)).thenReturn(false);

    // when
    final boolean result = condition.matches(context, metadata);

    // then
    assertThat(result).isFalse();
  }

  @Test
  void testShouldNotMatchWhenUnifiedPropertyIsFalse() {
    // given
    when(environment.getProperty(UNIFIED_PROPERTY, Boolean.class, true)).thenReturn(false);
    when(environment.getProperty(LEGACY_PROPERTY, Boolean.class, true)).thenReturn(true);

    // when
    final boolean result = condition.matches(context, metadata);

    // then
    assertThat(result).isFalse();
  }
}
