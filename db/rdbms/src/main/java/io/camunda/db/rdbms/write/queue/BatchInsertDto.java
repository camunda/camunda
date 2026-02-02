/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.queue;

import io.camunda.db.rdbms.write.domain.Copyable;
import io.camunda.util.ObjectBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public record BatchInsertDto<M>(List<M> dbModels) implements Copyable<BatchInsertDto<M>> {

  public BatchInsertDto(final M dbModel) {
    this(new ArrayList<>());
    dbModels.add(dbModel);
  }

  public BatchInsertDto<M> withAdditionalDbModel(final M dbModel) {
    return new Builder().dbModels(new ArrayList<>(dbModels)).dbModel(dbModel).build();
  }

  @Override
  public BatchInsertDto<M> copy(
      final Function<ObjectBuilder<BatchInsertDto<M>>, ObjectBuilder<BatchInsertDto<M>>>
          copyFunction) {
    return copyFunction.apply(new Builder().dbModels(new ArrayList<>(dbModels))).build();
  }

  public class Builder implements ObjectBuilder<BatchInsertDto<M>> {

    private List<M> dbModels = new ArrayList<>();

    public Builder dbModel(final M dbModel) {
      dbModels.add(dbModel);
      return this;
    }

    public Builder dbModels(final List<M> dbModels) {
      this.dbModels = dbModels;
      return this;
    }

    @Override
    public BatchInsertDto<M> build() {
      return new BatchInsertDto<>(dbModels);
    }
  }
}
