/*
 * Zeebe Broker Core
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
package io.zeebe.broker.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class KeyStateControllerTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private KeyStateController stateController;

  @Before
  public void setUp() throws Exception {
    stateController = new KeyStateController();
    stateController.open(folder.newFolder("rocksdb"), false);
  }

  @Test
  public void shouldGetDefaultIfNoLatest() {
    // given

    // when
    final long latestKey = stateController.getNextKey();

    // then
    assertThat(latestKey).isEqualTo(Long.MIN_VALUE);
  }

  @Test
  public void shouldGetLatestKey() {
    // given
    stateController.putNextKey(12L);

    // when
    final long latestKey = stateController.getNextKey();

    // then
    assertThat(latestKey).isEqualTo(12L);
  }
}
