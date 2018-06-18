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
package io.zeebe.logstreams.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.zeebe.logstreams.log.LoggedEvent;
import org.junit.Test;

public class EventFilterTest {

  @Test
  public void testConjunction() {
    // given
    final EventFilter acceptFilter = (e) -> true;
    final EventFilter rejectFilter = (e) -> false;

    final LoggedEvent event = mock(LoggedEvent.class);

    // when/then
    assertThat(acceptFilter.and(acceptFilter).applies(event)).isTrue();
    assertThat(acceptFilter.and(rejectFilter).applies(event)).isFalse();
    assertThat(rejectFilter.and(rejectFilter).applies(event)).isFalse();
  }
}
