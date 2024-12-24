/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.annotation.value;

import io.camunda.spring.client.bean.BeanInfo;

/**
 * Common type for all annotation values.
 *
 * @param <B> either {@link io.camunda.spring.client.bean.ClassInfo} or {@link
 *     io.camunda.spring.client.bean.MethodInfo}.
 */
public interface CamundaAnnotationValue<B extends BeanInfo> {

  /**
   * @return the context of this annotation.
   */
  B getBeanInfo();
}
