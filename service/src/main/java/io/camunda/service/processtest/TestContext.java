/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.processtest;

import java.util.HashMap;
import java.util.Map;

public class TestContext {

  private final Map<String, Long> processInstanceKeyByAlias = new HashMap<>();

  public void addProcessInstance(final String alias, final long processInstanceKey) {
    processInstanceKeyByAlias.put(alias, processInstanceKey);
  }

  public long getProcessInstanceByAlias(final String alias) {
    return processInstanceKeyByAlias.get(alias);
  }
}
