/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;

public final record DateValueFilter(OffsetDateTime after, OffsetDateTime before)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<DateValueFilter> {

    private OffsetDateTime after;
    private OffsetDateTime before;

    public Builder after(final OffsetDateTime value) {
      after = value;
      return this;
    }

    public Builder before(final OffsetDateTime value) {
      before = value;
      return this;
    }

    @Override
    public DateValueFilter build() {
      return new DateValueFilter(after, before);
    }
  }
}
