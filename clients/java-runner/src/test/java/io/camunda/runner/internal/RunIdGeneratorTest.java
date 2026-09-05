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
package io.camunda.runner.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class RunIdGeneratorTest {

  @Test
  void shouldProduceFormatUserDashShort() {
    // when
    final String id = RunIdGenerator.generate("stephan");

    // then
    assertThat(id).matches("^[a-zA-Z0-9_]+-[a-z0-9]{5}$");
  }

  @Test
  void shouldProduceUniqueIdsAcrossCalls() {
    // when
    final Set<String> ids = new HashSet<>();
    for (int i = 0; i < 100; i++) {
      ids.add(RunIdGenerator.generate("stephan"));
    }

    // then
    assertThat(ids).hasSizeGreaterThanOrEqualTo(99);
  }
}
