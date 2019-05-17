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
package io.zeebe.broker.exporter.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.db.ZeebeDb;
import io.zeebe.engine.state.DefaultZeebeDbFactory;
import io.zeebe.test.util.AutoCloseableRule;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public class ExportersStateTest {

  private final AutoCloseableRule autoCloseableRule = new AutoCloseableRule();
  private final TemporaryFolder temporaryFolder = new TemporaryFolder();
  private ExportersState state;

  @Rule
  public final RuleChain chain = RuleChain.outerRule(temporaryFolder).around(autoCloseableRule);

  private ZeebeDb<ExporterColumnFamilies> db;

  @Before
  public void setup() throws Exception {
    final File dbDirectory = temporaryFolder.newFolder();
    db = DefaultZeebeDbFactory.defaultFactory(ExporterColumnFamilies.class).createDb(dbDirectory);
    state = new ExportersState(db, db.createContext());
  }

  @After
  public void tearDown() throws Exception {
    db.close();
  }

  @Test
  public void shouldSetPositionForNewExporter() {
    // given
    final String id = "exporter";
    final long position = 123;

    // when
    state.setPosition(id, position);

    // then
    assertThat(state.getPosition(id)).isEqualTo(position);
  }

  @Test
  public void shouldOverwritePositionOfExporter() {
    // given
    final String id = "exporter";
    final long firstPosition = 123;
    final long secondPosition = 2034;

    // when
    state.setPosition(id, firstPosition);
    state.setPosition(id, secondPosition);

    // then
    assertThat(state.getPosition(id)).isEqualTo(secondPosition);
  }

  @Test
  public void shouldReturnUnknownPositionForUnknownExporter() {
    // given
    final String id = "exporter";

    // when
    final long position = state.getPosition(id);

    // then
    assertThat(position).isEqualTo(-1);
  }

  @Test
  public void shouldSetPositionSinceSomethingIsGreaterThanNothing() {
    // given
    final String id = "exporter";
    final long position = 12312;

    // when
    state.setPositionIfGreater(id, position);

    // then
    assertThat(state.getPosition(id)).isEqualTo(position);
  }

  @Test
  public void shouldNotSetPositionIfLowerThanExisting() {
    // given
    final String id = "exporter";
    final long position = 12313;

    // when
    state.setPosition(id, position);
    state.setPositionIfGreater(id, position - 1);

    // then
    assertThat(state.getPosition(id)).isEqualTo(position);
  }

  @Test
  public void shouldSetPositionIfGreaterThanExisting() {
    // given
    final String id = "exporter";
    final long position = 123;

    // when
    state.setPosition(id, position - 1);
    state.setPositionIfGreater(id, position);

    // then
    assertThat(state.getPosition(id)).isEqualTo(position);
  }

  @Test
  public void shouldRemovePosition() {
    // given
    state.setPosition("e1", 1L);
    state.setPosition("e2", 2L);

    // when
    state.removePosition("e2");

    // then
    assertThat(state.getPosition("e1")).isEqualTo(1L);
    assertThat(state.getPosition("e2")).isEqualTo(-1);
  }

  @Test
  public void shouldVisitPositions() {
    // given
    state.setPosition("e1", 1L);
    state.setPosition("e2", 2L);

    // when
    final Map<String, Long> positions = new HashMap<>();
    state.visitPositions((exporterId, position) -> positions.put(exporterId, position));

    // then
    assertThat(positions).hasSize(2).contains(entry("e1", 1L), entry("e2", 2L));
  }

  @Test
  public void shouldGetLowestPosition() {
    // given
    state.setPosition("e2", -1L);
    state.setPosition("e1", 1L);

    // when/then
    assertThat(state.getLowestPosition()).isEqualTo(-1L);
  }
}
