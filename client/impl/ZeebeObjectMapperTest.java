/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.client.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.util.ClientTest;
import org.junit.Test;

public class ZeebeObjectMapperTest extends ClientTest {

  @Test
  public void shouldReturnEmptyObject() {
    // Given
    final ZeebeObjectMapper zeebeObjectMapper = new ZeebeObjectMapper();
    final TestObject testObject = new TestObject();
    testObject.setObject(new Object());
    // When
    final String actual = zeebeObjectMapper.toJson(testObject);
    // Then
    assertThat(actual).isEqualTo("{\"object\":{}}");
  }

  public static final class TestObject {
    private Object object;

    public Object getObject() {
      return object;
    }

    public void setObject(final Object object) {
      this.object = object;
    }
  }
}
