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
package io.zeebe.broker.job.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.job.JobStateController;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class JobStateControllerTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private JobStateController stateController;

  @Before
  public void setUp() throws Exception {
    stateController = new JobStateController();
    stateController.open(folder.newFolder("rocksdb"), false);
  }

  @After
  public void close() {
    stateController.close();
  }

  @Test
  public void shouldGetJobsInOrder() {
    // given
    stateController.activate(1, createJobWithDeadline(100L));
    stateController.activate(2, createJobWithDeadline(256L));

    // when
    final List<Long> jobKeys = new ArrayList<>();
    stateController.forEachTimedOutEntry(150L, ((key, record, control) -> jobKeys.add(key)));

    // then
    assertThat(jobKeys).hasSize(1);
    assertThat(jobKeys).containsExactly(1L);
  }

  public JobRecord createJobWithDeadline(long deadline) {
    final JobRecord jobRecord = new JobRecord();
    jobRecord.setDeadline(deadline);
    return jobRecord;
  }
}
