/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.util;

import io.camunda.zeebe.client.api.command.ClientStatusException;
import io.grpc.Status.Code;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

public final class StatusCodeMatcher extends TypeSafeMatcher<ClientStatusException> {

  private final Code expectedCode;

  public StatusCodeMatcher(final Code expectedCode) {
    this.expectedCode = expectedCode;
  }

  public static StatusCodeMatcher hasStatusCode(final Code expectedCode) {
    return new StatusCodeMatcher(expectedCode);
  }

  @Override
  protected boolean matchesSafely(final ClientStatusException item) {
    return item.getStatusCode().equals(expectedCode);
  }

  @Override
  protected void describeMismatchSafely(
      final ClientStatusException item, final Description mismatchDescription) {
    mismatchDescription.appendText("was ").appendValue(item.getStatusCode());
  }

  @Override
  public void describeTo(final Description description) {
    description.appendText("status code to be ").appendValue(expectedCode);
  }
}
