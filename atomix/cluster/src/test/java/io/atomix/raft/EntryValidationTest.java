/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.raft;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.raft.storage.log.entry.ApplicationEntry;
import io.atomix.raft.zeebe.EntryValidator;
import io.atomix.raft.zeebe.EntryValidator.ValidationResult;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import org.junit.Rule;
import org.junit.Test;

public final class EntryValidationTest {

  private final TestEntryValidator entryValidator = new TestEntryValidator();

  @Rule
  public RaftRule raftRule = RaftRule.withBootstrappedNodes(3).setEntryValidator(entryValidator);

  @Test
  public void shouldValidateEntryWithLastAfterFailOver() throws Exception {
    // given
    entryValidator.validation =
        (last, current) -> {
          assertThat(last).isNull();
          return ValidationResult.ok();
        };
    raftRule.appendEntry();

    final CountDownLatch latch = new CountDownLatch(1);
    entryValidator.validation =
        (last, current) -> {
          assertThat(last).isNotNull();
          assertThat(last.lowestPosition()).isEqualTo(1);
          assertThat(last.highestPosition()).isEqualTo(11);
          latch.countDown();

          return ValidationResult.ok();
        };
    raftRule.shutdownLeader();
    raftRule.awaitNewLeader();

    // when
    raftRule.appendEntry();

    // then
    assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
  }

  private static class TestEntryValidator implements EntryValidator {
    BiFunction<ApplicationEntry, ApplicationEntry, ValidationResult> validation;

    @Override
    public ValidationResult validateEntry(
        final ApplicationEntry lastEntry, final ApplicationEntry entry) {
      return validation.apply(lastEntry, entry);
    }
  }
}
