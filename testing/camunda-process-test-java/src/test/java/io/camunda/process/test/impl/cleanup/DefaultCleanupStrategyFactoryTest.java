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
package io.camunda.process.test.impl.cleanup;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.process.test.api.DataDeletionMode;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DefaultCleanupStrategyFactoryTest {

  private final DefaultCleanupStrategyFactory factory = new DefaultCleanupStrategyFactory();

  @ParameterizedTest
  @MethodSource("dataDeletionModes")
  void shouldCreateCleanupStrategyForDataDeletionMode(
      final DataDeletionMode dataDeletionMode,
      final Class<? extends CleanupStrategy> expectedType) {
    // given

    // when
    final CleanupStrategy strategy = factory.create(dataDeletionMode);

    // then
    assertThat(strategy).isInstanceOf(expectedType);
  }

  private static Stream<Arguments> dataDeletionModes() {
    return Stream.of(
        Arguments.of(DataDeletionMode.CLUSTER_PURGE, ClusterPurgeCleanupStrategy.class),
        Arguments.of(
            DataDeletionMode.RESOURCE_AND_HISTORY_DELETION,
            ResourceAndHistoryDeletionStrategy.class),
        Arguments.of(DataDeletionMode.NONE, NoOpCleanupStrategy.class));
  }
}
