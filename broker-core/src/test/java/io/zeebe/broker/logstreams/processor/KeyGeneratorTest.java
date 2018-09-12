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
package io.zeebe.broker.logstreams.processor;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.util.KeyStateController;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class KeyGeneratorTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private KeyStateController stateController;
  private KeyGenerator keyGenerator;

  @Before
  public void setUp() {
    stateController = new KeyStateController();
    keyGenerator = new KeyGenerator(0, 1, stateController);
  }

  @Test
  public void shouldInitKeyStateOnOpen() throws Exception {
    // given

    // when
    stateController.open(folder.newFolder("rocksdb"), false);

    // then
    assertThat(stateController.getNextKey()).isEqualTo(0);
  }

  @Test
  public void shouldGenerateNextKey() throws Exception {
    // given
    stateController.open(folder.newFolder("rocksdb"), false);

    // when
    final long nextKey = keyGenerator.nextKey();

    // then
    assertThat(nextKey).isEqualTo(0);
    assertThat(stateController.getNextKey()).isEqualTo(1);
  }

  @Test
  public void shouldGenerateNextKetWithoutStateController() {
    // given
    final KeyGenerator keyGenerator = new KeyGenerator(0, 1);

    // when
    final long key = keyGenerator.nextKey();

    // then
    assertThat(key).isEqualTo(0);
  }

  @Test
  public void shouldSetKey() {
    // given
    final KeyGenerator keyGenerator = new KeyGenerator(0, 1);
    keyGenerator.setKey(1L);

    // when
    final long key = keyGenerator.nextKey();

    // then
    assertThat(key).isEqualTo(2L);
  }
}
