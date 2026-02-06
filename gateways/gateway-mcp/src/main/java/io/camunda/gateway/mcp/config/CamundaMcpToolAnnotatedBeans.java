/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registry for beans that have methods annotated with {@link CamundaMcpTool}.
 *
 * <p>This registry is populated by {@link CamundaMcpToolBeanPostProcessor} during Spring's bean
 * initialization phase. It provides efficient access to tool beans without scanning the entire
 * application context at runtime.
 */
public class CamundaMcpToolAnnotatedBeans {

  private final List<Object> toolBeans = new ArrayList<>();

  /**
   * Registers a bean that contains at least one method annotated with {@link CamundaMcpTool}.
   *
   * @param bean the bean to register
   */
  public void addToolBean(final Object bean) {
    toolBeans.add(bean);
  }

  /**
   * Returns all registered tool beans.
   *
   * @return unmodifiable list of tool beans
   */
  public List<Object> getToolBeans() {
    return Collections.unmodifiableList(toolBeans);
  }

  /**
   * Returns the number of registered tool beans.
   *
   * @return count of tool beans
   */
  public int getCount() {
    return toolBeans.size();
  }
}
