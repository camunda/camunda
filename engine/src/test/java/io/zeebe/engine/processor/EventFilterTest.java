/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.processor;

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
