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
package io.camunda.process.test.impl.testCases;

import io.camunda.process.test.api.testCases.TestCaseReservationToken;
import java.util.UUID;

public final class TestRunContext {

  private final String reservationToken = "cpt-" + UUID.randomUUID();

  public static TestRunContext create() {
    return new TestRunContext();
  }

  public static TestRunContext disabled() {
    return new TestRunContext();
  }

  public String resolveReservationToken(final String token) {
    return TestCaseReservationToken.PLACEHOLDER.equals(token) ? reservationToken : token;
  }
}
