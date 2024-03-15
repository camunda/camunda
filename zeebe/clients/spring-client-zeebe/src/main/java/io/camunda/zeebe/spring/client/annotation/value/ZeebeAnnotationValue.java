/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.spring.client.annotation.value;

import io.camunda.zeebe.spring.client.bean.BeanInfo;

/**
 * Common type for all annotation values.
 *
 * @param <B> either {@link io.camunda.zeebe.spring.client.bean.ClassInfo} or {@link
 *     io.camunda.zeebe.spring.client.bean.MethodInfo}.
 */
public interface ZeebeAnnotationValue<B extends BeanInfo> {

  /**
   * @return the context of this annotation.
   */
  B getBeanInfo();
}
