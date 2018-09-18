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
package io.zeebe.broker.workflow.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class WorkflowStateTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private WorkflowState workflowState;

  @Before
  public void setUp() throws Exception {
    workflowState = new WorkflowState();
    workflowState.open(folder.newFolder("rocksdb"), false);
  }

  @Test
  public void shouldGetNextWorkflowKey() {
    // given

    // when
    final long nextWorkflowKey = workflowState.getNextWorkflowKey();

    // then
    assertThat(nextWorkflowKey).isEqualTo(1L);
  }

  @Test
  public void shouldIncrementWorkflowKey() {
    // given
    workflowState.getNextWorkflowKey();

    // when
    final long nextWorkflowKey = workflowState.getNextWorkflowKey();

    // then
    assertThat(nextWorkflowKey).isEqualTo(2L);
  }

  @Test
  public void shouldGetNextWorkflowVersion() {
    // given

    // when
    final long nextWorkflowVersion = workflowState.getNextWorkflowVersion("foo");

    // then
    assertThat(nextWorkflowVersion).isEqualTo(1L);
  }

  @Test
  public void shouldIncrementWorkflowVersion() {
    // given
    workflowState.getNextWorkflowVersion("foo");

    // when
    final long nextWorkflowVersion = workflowState.getNextWorkflowVersion("foo");

    // then
    assertThat(nextWorkflowVersion).isEqualTo(2L);
  }

  @Test
  public void shouldNotIncrementWorkflowVersionForDifferentProcessId() {
    // given
    workflowState.getNextWorkflowVersion("foo");

    // when
    final long nextWorkflowVersion = workflowState.getNextWorkflowVersion("bar");

    // then
    assertThat(nextWorkflowVersion).isEqualTo(1L);
  }
}
