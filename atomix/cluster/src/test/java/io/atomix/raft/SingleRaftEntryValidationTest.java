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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.raft.storage.log.entry.InitializeEntry;
import io.atomix.raft.zeebe.EntryValidator;
import io.atomix.raft.zeebe.ValidationResult;
import io.atomix.raft.zeebe.ZeebeEntry;
import io.zeebe.test.util.TestUtil;
import java.util.Collection;
import java.util.function.BiFunction;
import org.junit.Rule;
import org.junit.Test;

public class SingleRaftEntryValidationTest {

  private final TestEntryValidator entryValidator = new TestEntryValidator();

  @Rule
  public RaftRule raftRule = RaftRule.withBootstrappedNodes(1).setEntryValidator(entryValidator);

  @Test
  public void shouldFailAppendOnInvalidEntry() {
    // given
    entryValidator.validation = (last, current) -> ValidationResult.failure("invalid");

    // when - then expect
    assertThatThrownBy(() -> raftRule.appendEntry()).hasMessageContaining("invalid");
  }

  @Test
  public void shouldNotAppendInvalidEntryToLog() throws Exception {
    // given
    entryValidator.validation = (last, current) -> ValidationResult.failure("invalid");

    // when
    assertThatThrownBy(() -> raftRule.appendEntry()).hasMessageContaining("invalid");
    entryValidator.validation = (last, current) -> ValidationResult.success();
    TestUtil.waitUntil(() -> getEntryTypeCount(InitializeEntry.class) == 2);
    raftRule.appendEntry();

    // then
    assertThat(getEntryTypeCount(ZeebeEntry.class)).isOne();
  }

  private int getEntryTypeCount(final Class type) {
    return (int)
        raftRule.getMemberLogs().values().stream()
            .flatMap(Collection::stream)
            .filter(e -> e.entry().getClass().isAssignableFrom(type))
            .count();
  }

  private static class TestEntryValidator implements EntryValidator {
    BiFunction<ZeebeEntry, ZeebeEntry, ValidationResult> validation;

    @Override
    public ValidationResult validateEntry(final ZeebeEntry lastEntry, final ZeebeEntry entry) {
      return validation.apply(lastEntry, entry);
    }
  }
}
