/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.util;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;

import io.camunda.zeebe.client.api.command.ClientStatusException;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public final class StatusDescriptionMatcher extends TypeSafeMatcher<ClientStatusException> {

  private final Matcher<String> expectedDescriptionMatcher;

  public StatusDescriptionMatcher(final Matcher<String> expectedDescriptionMatcher) {
    this.expectedDescriptionMatcher = expectedDescriptionMatcher;
  }

  public static StatusDescriptionMatcher descriptionIs(final String description) {
    return new StatusDescriptionMatcher(equalTo(description));
  }

  public static StatusDescriptionMatcher descriptionContains(final String substring) {
    return new StatusDescriptionMatcher(containsString(substring));
  }

  public static StatusDescriptionMatcher descriptionMatches(
      final Matcher<String> expectedDescriptionMatcher) {
    return new StatusDescriptionMatcher(expectedDescriptionMatcher);
  }

  @Override
  protected boolean matchesSafely(final ClientStatusException item) {
    return expectedDescriptionMatcher.matches(item.getStatus().getDescription());
  }

  @Override
  protected void describeMismatchSafely(
      final ClientStatusException item, final Description mismatchDescription) {
    expectedDescriptionMatcher.describeMismatch(
        item.getStatus().getDescription(), mismatchDescription);
  }

  @Override
  public void describeTo(final Description description) {
    description.appendText("status description to be ");
    expectedDescriptionMatcher.describeTo(description);
  }
}
