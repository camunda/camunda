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
package io.zeebe.servicecontainer.impl;

import static org.assertj.core.api.Java6Assertions.assertThat;

import io.zeebe.util.sched.future.ActorFuture;

public class ActorFutureAssertions {
  protected static void assertCompleted(final ActorFuture<?> serviceFuture) {
    assertThat(serviceFuture).isDone();
  }

  protected static void assertNotCompleted(final ActorFuture<?> serviceFuture) {
    assertThat(serviceFuture).isNotDone();
  }

  protected static void assertFailed(final ActorFuture<?> serviceFuture) {
    assertThat(serviceFuture.isCompletedExceptionally()).isTrue();
  }
}
