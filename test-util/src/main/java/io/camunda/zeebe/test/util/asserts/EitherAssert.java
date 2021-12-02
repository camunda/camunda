/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.asserts;

import io.camunda.zeebe.util.Either;
import org.assertj.core.api.AbstractObjectAssert;

public final class EitherAssert<L, R>
    extends AbstractObjectAssert<EitherAssert<L, R>, Either<L, R>> {
  public EitherAssert(final Either<L, R> actual) {
    super(actual, EitherAssert.class);
  }

  public static <L, R> EitherAssert<L, R> assertThat(final Either<L, R> actual) {
    return new EitherAssert<>(actual);
  }

  public EitherAssert<L, R> isRight() {
    if (actual.isLeft()) {
      failWithMessage("Expected <%s> to be right, but was left <%s>", actual, actual.getLeft());
    }

    return myself;
  }

  public EitherAssert<L, R> isLeft() {
    if (actual.isRight()) {
      failWithMessage("Expected <%s> to be left, but was right <%s>", actual, actual.get());
    }

    return myself;
  }
}
