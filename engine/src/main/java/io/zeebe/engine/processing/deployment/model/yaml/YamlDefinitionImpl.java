/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.deployment.model.yaml;

import java.util.ArrayList;
import java.util.List;

public final class YamlDefinitionImpl {
  private String name = "";

  private List<YamlTask> tasks = new ArrayList<>();

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public List<YamlTask> getTasks() {
    return tasks;
  }

  public void setTasks(final List<YamlTask> tasks) {
    this.tasks = tasks;
  }
}
