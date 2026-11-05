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
package io.camunda.process.test.impl.testCases.instructions;

import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.impl.testCases.TestRunContext;
import java.util.Optional;

/**
 * Makes the job commands of one instruction carry exactly the reservation token the instruction
 * names, and no token afterwards. A token written in a test case is a placeholder the run context
 * resolves; it must not outlive the instruction it was written on, or a later instruction without a
 * token would send it too.
 */
final class JobReservationTokenScope {

  private JobReservationTokenScope() {}

  static void run(
      final CamundaProcessTestContext context,
      final TestRunContext runContext,
      final Optional<String> token,
      final Runnable action) {
    if (!token.isPresent()) {
      action.run();
      return;
    }

    context.setJobReservationToken(runContext.resolveJobReservationToken(token.get()));
    try {
      action.run();
    } finally {
      context.setJobReservationToken(null);
    }
  }
}
