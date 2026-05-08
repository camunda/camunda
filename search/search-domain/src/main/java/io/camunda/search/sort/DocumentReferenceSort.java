/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.sort;

import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.function.Function;

public final record DocumentReferenceSort(List<FieldSorting> orderings) implements SortOption {

  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static DocumentReferenceSort of(
      final Function<Builder, ObjectBuilder<DocumentReferenceSort>> fn) {
    return SortOptionBuilders.documentReference(fn);
  }

  public static final class Builder extends AbstractBuilder<Builder>
      implements ObjectBuilder<DocumentReferenceSort> {

    public Builder processInstanceKey() {
      currentOrdering = new FieldSorting("processInstanceKey", null);
      return this;
    }

    public Builder scopeKey() {
      currentOrdering = new FieldSorting("scopeKey", null);
      return this;
    }

    public Builder variableKey() {
      currentOrdering = new FieldSorting("variableKey", null);
      return this;
    }

    public Builder documentId() {
      currentOrdering = new FieldSorting("documentId", null);
      return this;
    }

    public Builder fileName() {
      currentOrdering = new FieldSorting("fileName", null);
      return this;
    }

    public Builder tenantId() {
      currentOrdering = new FieldSorting("tenantId", null);
      return this;
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public DocumentReferenceSort build() {
      return new DocumentReferenceSort(orderings);
    }
  }
}
