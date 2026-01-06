/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.util.ObjectBuilder;
import java.util.function.Function;

public record ProcessInstanceTagDbModel(Long processInstanceKey, String tagValue)
    implements DbModel<ProcessInstanceTagDbModel> {

  @Override
  public ProcessInstanceTagDbModel copy(
      final Function<
              ObjectBuilder<ProcessInstanceTagDbModel>, ObjectBuilder<ProcessInstanceTagDbModel>>
          builderFunction) {
    return builderFunction
        .apply(
            new ProcessInstanceTagDbModelBuilder()
                .processInstanceKey(processInstanceKey)
                .tagValue(tagValue))
        .build();
  }

  public static class ProcessInstanceTagDbModelBuilder
      implements ObjectBuilder<ProcessInstanceTagDbModel> {

    private Long processInstanceKey;
    private String tagValue;

    // Public constructor to initialize the builder
    public ProcessInstanceTagDbModelBuilder() {}

    // Builder methods for each field
    public ProcessInstanceTagDbModelBuilder processInstanceKey(final Long processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    public ProcessInstanceTagDbModelBuilder tagValue(final String tagValue) {
      this.tagValue = tagValue;
      return this;
    }

    @Override
    public ProcessInstanceTagDbModel build() {
      return new ProcessInstanceTagDbModel(processInstanceKey, tagValue);
    }
  }
}
