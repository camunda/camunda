/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.util;

import io.grpc.Status.Code;
import io.zeebe.client.api.command.ClientStatusException;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

public class StatusCodeMatcher extends TypeSafeMatcher<ClientStatusException> {

  private final Code expectedCode;

  public StatusCodeMatcher(Code expectedCode) {
    this.expectedCode = expectedCode;
  }

  public static StatusCodeMatcher hasStatusCode(Code expectedCode) {
    return new StatusCodeMatcher(expectedCode);
  }

  @Override
  protected boolean matchesSafely(ClientStatusException item) {
    return item.getStatusCode().equals(expectedCode);
  }

  @Override
  protected void describeMismatchSafely(
      ClientStatusException item, Description mismatchDescription) {
    mismatchDescription.appendText("was ").appendValue(item.getStatusCode());
  }

  @Override
  public void describeTo(Description description) {
    description.appendText("status code to be ").appendValue(expectedCode);
  }
}
