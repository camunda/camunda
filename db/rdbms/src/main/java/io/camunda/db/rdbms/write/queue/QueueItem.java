/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.queue;

import java.util.function.Function;

public record QueueItem(
    ContextType contextType,
    WriteStatementType statementType,
    Object id,
    String statementId,
    Object parameter) {

  public QueueItem copy(final Function<QueueItemBuilder, QueueItemBuilder> builderFunction) {
    return builderFunction
        .apply(
            new QueueItemBuilder()
                .statementType(statementType)
                .contextType(contextType)
                .statementType(statementType)
                .id(id)
                .statementId(statementId)
                .parameter(parameter))
        .build();
  }

  // Builder
  public static class QueueItemBuilder {

    private ContextType contextType;
    private WriteStatementType statementType;
    private Object id;
    private String statementId;
    private Object parameter;

    public QueueItemBuilder contextType(final ContextType contextType) {
      this.contextType = contextType;
      return this;
    }

    public QueueItemBuilder statementType(final WriteStatementType statementType) {
      this.statementType = statementType;
      return this;
    }

    public QueueItemBuilder id(final Object id) {
      this.id = id;
      return this;
    }

    public QueueItemBuilder statementId(final String statementId) {
      this.statementId = statementId;
      return this;
    }

    public QueueItemBuilder parameter(final Object parameter) {
      this.parameter = parameter;
      return this;
    }

    public QueueItem build() {
      return new QueueItem(contextType, statementType, id, statementId, parameter);
    }
  }
}
