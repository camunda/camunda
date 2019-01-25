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

import io.grpc.Status.Code;
import io.zeebe.client.cmd.ClientStatusException;
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
  public void describeTo(Description description) {
    description.appendText("status code to be ").appendValue(expectedCode);
  }

  @Override
  protected void describeMismatchSafely(
      ClientStatusException item, Description mismatchDescription) {
    mismatchDescription.appendText("was ").appendValue(item.getStatusCode());
  }
}
