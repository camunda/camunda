/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.broker.it.util;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;

import io.zeebe.client.cmd.ClientStatusException;
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
  public void describeTo(Description description) {
    description.appendText("status description to be ");
    expectedDescriptionMatcher.describeTo(description);
  }

  @Override
  protected void describeMismatchSafely(
      ClientStatusException item, Description mismatchDescription) {
    expectedDescriptionMatcher.describeMismatch(
        item.getStatus().getDescription(), mismatchDescription);
  }
}
