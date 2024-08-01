/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.source;

import io.camunda.service.search.source.ProcessInstanceSourceConfig.Builder;
import io.camunda.util.ObjectBuilder;
import java.util.function.Function;

public final class SourceConfigBuilders {

  private SourceConfigBuilders() {}

  public static ProcessInstanceSourceConfig.Builder processInstance() {
    return new ProcessInstanceSourceConfig.Builder();
  }

  public static ProcessInstanceSourceConfig processInstance(
      final Function<Builder, ObjectBuilder<ProcessInstanceSourceConfig>> fn) {
    return fn.apply(processInstance()).build();
  }
}
