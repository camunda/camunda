/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.util;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;

import io.zeebe.client.api.command.ClientStatusException;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public class StatusDescriptionMatcher extends TypeSafeMatcher<ClientStatusException> {

  private final Matcher<String> expectedDescriptionMatcher;

  public StatusDescriptionMatcher(Matcher<String> expectedDescriptionMatcher) {
    this.expectedDescriptionMatcher = expectedDescriptionMatcher;
  }

  public static StatusDescriptionMatcher descriptionIs(String description) {
    return new StatusDescriptionMatcher(equalTo(description));
  }

  public static StatusDescriptionMatcher descriptionContains(String substring) {
    return new StatusDescriptionMatcher(containsString(substring));
  }

  public static StatusDescriptionMatcher descriptionMatches(
      Matcher<String> expectedDescriptionMatcher) {
    return new StatusDescriptionMatcher(expectedDescriptionMatcher);
  }

  @Override
  protected boolean matchesSafely(ClientStatusException item) {
    return expectedDescriptionMatcher.matches(item.getStatus().getDescription());
  }

  @Override
  protected void describeMismatchSafely(
      ClientStatusException item, Description mismatchDescription) {
    expectedDescriptionMatcher.describeMismatch(
        item.getStatus().getDescription(), mismatchDescription);
  }

  @Override
  public void describeTo(Description description) {
    description.appendText("status description to be ");
    expectedDescriptionMatcher.describeTo(description);
  }
}
