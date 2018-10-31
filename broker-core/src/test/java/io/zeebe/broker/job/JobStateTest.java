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
package io.zeebe.broker.job;

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.job.JobState.State;
import io.zeebe.broker.logstreams.state.ZeebeState;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class JobStateTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private JobState stateController;
  private ZeebeState zeebeState;

  @Before
  public void setUp() throws Exception {
    zeebeState = new ZeebeState();
    zeebeState.open(folder.newFolder("db"), false);
    stateController = zeebeState.getJobState();
  }

  @After
  public void tearDown() {
    zeebeState.close();
  }

  @Test
  public void shouldCreateJobEntry() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    stateController.create(key, jobRecord);

    // then
    assertThat(stateController.exists(key)).isTrue();
    assertJobState(key, State.ACTIVATABLE);
    assertJobRecordIsEqualTo(stateController.getJob(key), jobRecord);
    assertListedAsActivatable(key, jobRecord.getType());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
  }

  @Test
  public void shouldActivateJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    stateController.create(key, jobRecord);
    stateController.activate(key, jobRecord);

    // then
    assertThat(stateController.exists(key)).isTrue();
    assertJobState(key, State.ACTIVATED);
    assertJobRecordIsEqualTo(stateController.getJob(key), jobRecord);
    refuteListedAsActivatable(key, jobRecord.getType());
    assertListedAsTimedOut(key, jobRecord.getDeadline() + 1);
  }

  @Test
  public void shouldTimeoutJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    stateController.create(key, jobRecord);
    stateController.activate(key, jobRecord);
    stateController.timeout(key, jobRecord);

    // then
    assertThat(stateController.exists(key)).isTrue();
    assertJobState(key, State.ACTIVATABLE);
    assertJobRecordIsEqualTo(stateController.getJob(key), jobRecord);
    assertListedAsActivatable(key, jobRecord.getType());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
  }

  @Test
  public void shouldDeleteActivatableJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    stateController.create(key, jobRecord);
    stateController.delete(key, jobRecord);

    // then
    assertThat(stateController.exists(key)).isFalse();
    assertThat(State.values()).noneMatch(state -> stateController.isInState(key, state));
    assertThat(stateController.getJob(key)).isNull();
    refuteListedAsActivatable(key, jobRecord.getType());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
  }

  @Test
  public void shouldDeleteActivatedJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    stateController.create(key, jobRecord);
    stateController.activate(key, jobRecord);
    stateController.delete(key, jobRecord);

    // then
    assertThat(stateController.exists(key)).isFalse();
    assertThat(State.values()).noneMatch(state -> stateController.isInState(key, state));
    assertThat(stateController.getJob(key)).isNull();
    refuteListedAsActivatable(key, jobRecord.getType());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
  }

  @Test
  public void shouldDeleteFailedJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    stateController.create(key, jobRecord);
    stateController.activate(key, jobRecord);
    stateController.fail(key, jobRecord.setRetries(0));
    stateController.delete(key, jobRecord);

    // then
    assertThat(stateController.exists(key)).isFalse();
    assertThat(State.values()).noneMatch(state -> stateController.isInState(key, state));
    assertThat(stateController.getJob(key)).isNull();
    refuteListedAsActivatable(key, jobRecord.getType());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
  }

  @Test
  public void shouldFailJobWithRetriesLeft() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord().setRetries(1);

    // when
    stateController.create(key, jobRecord);
    stateController.activate(key, jobRecord);
    stateController.fail(key, jobRecord);

    // then
    assertThat(stateController.exists(key)).isTrue();
    assertJobState(key, State.ACTIVATABLE);
    assertJobRecordIsEqualTo(stateController.getJob(key), jobRecord);
    assertListedAsActivatable(key, jobRecord.getType());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
  }

  @Test
  public void shouldFailJobWithNoRetriesLeft() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord().setRetries(0);

    // when
    stateController.create(key, jobRecord);
    stateController.activate(key, jobRecord);
    stateController.fail(key, jobRecord);

    // then
    assertThat(stateController.exists(key)).isTrue();
    assertJobState(key, State.FAILED);
    assertJobRecordIsEqualTo(stateController.getJob(key), jobRecord);
    refuteListedAsActivatable(key, jobRecord.getType());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
  }

  @Test
  public void shouldResolveJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    stateController.create(key, jobRecord);
    stateController.activate(key, jobRecord);
    stateController.fail(key, jobRecord.setRetries(0));
    stateController.resolve(key, jobRecord);

    // then
    assertThat(stateController.exists(key)).isTrue();
    assertJobState(key, State.ACTIVATABLE);
    assertJobRecordIsEqualTo(stateController.getJob(key), jobRecord);
    assertListedAsActivatable(key, jobRecord.getType());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
  }

  @Test
  public void shouldListTimedOutEntriesInOrder() {
    // given
    createAndActivateJobRecord(1, newJobRecord().setDeadline(1L));
    createAndActivateJobRecord(2, newJobRecord().setDeadline(256L));
    stateController.create(5, newJobRecord().setDeadline(512L));
    createAndActivateJobRecord(3, newJobRecord().setDeadline(65536L));
    createAndActivateJobRecord(4, newJobRecord().setDeadline(4294967296L));

    // when
    final List<Long> jobKeys = getTimedOutKeys(32768L);

    // then
    assertThat(jobKeys).hasSize(2);
    assertThat(jobKeys).containsExactly(1L, 2L);
  }

  @Test
  public void shouldDoNothingIfNotTimedOutJobs() {
    // given
    stateController.create(5, newJobRecord().setDeadline(512L));
    createAndActivateJobRecord(4, newJobRecord().setDeadline(4294967296L));

    // when
    final List<Long> jobKeys = getTimedOutKeys(32768L);

    // then
    assertThat(jobKeys).isEmpty();
  }

  @Test
  public void shouldCheckExistenceCorrectly() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    stateController.create(key, jobRecord);

    // then
    assertThat(stateController.exists(key)).isTrue();
    assertThat(stateController.exists(key + 1)).isFalse();
  }

  @Test
  public void shouldListActivatableJobsForTypeInOrder() {
    // given
    final DirectBuffer type = wrapString("test");
    stateController.create(1, newJobRecord().setType("tes"));
    stateController.create(256L, newJobRecord().setType(type));
    createAndActivateJobRecord(512, newJobRecord().setType(type));
    stateController.create(65536L, newJobRecord().setType(type));
    stateController.create(4294967296L, newJobRecord().setType("test-other"));

    // when
    final List<Long> jobKeys = getActivatableKeys(type);

    // then
    assertThat(jobKeys).hasSize(2);
    assertThat(jobKeys).containsExactly(256L, 65536L);
  }

  @Test
  public void shouldNotDoAnythingIfNoActivatableJobs() {
    // given
    final DirectBuffer type = wrapString("test");
    createAndActivateJobRecord(1, newJobRecord().setType(type));
    stateController.create(256L, newJobRecord().setType("other"));

    // when
    final List<Long> jobKeys = getActivatableKeys(type);

    // then
    assertThat(jobKeys).isEmpty();
  }

  @Test
  public void shouldReturnNullIfJobDoesNotExist() {
    // given
    final long key = 1L;

    // then
    assertThat(stateController.getJob(key)).isNull();
  }

  @Test
  public void shouldReturnCorrectJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord().setType("test");

    // when
    stateController.create(key, jobRecord);
    stateController.create(key + 1, newJobRecord().setType("other"));

    // then
    final JobRecord savedJob = stateController.getJob(key);
    assertJobRecordIsEqualTo(savedJob, jobRecord);
    assertThat(BufferUtil.bufferAsString(savedJob.getType())).isEqualTo("test");
  }

  private void createAndActivateJobRecord(final long key, final JobRecord record) {
    stateController.create(key, record);
    stateController.activate(key, record);
  }

  private JobRecord newJobRecord() {
    final JobRecord jobRecord = new JobRecord();

    jobRecord.setRetries(2);
    jobRecord.setDeadline(256L);
    jobRecord.setType("test");

    return jobRecord;
  }

  private void assertJobState(final long key, final JobState.State state) {
    final List<State> others =
        Arrays.stream(State.values()).filter(s -> s != state).collect(Collectors.toList());

    assertThat(stateController.isInState(key, state)).isTrue();
    assertThat(others).noneMatch(other -> stateController.isInState(key, other));
  }

  private void assertJobRecordIsEqualTo(final JobRecord jobRecord, final JobRecord expected) {
    assertThat(jobRecord.getDeadline()).isEqualTo(expected.getDeadline());
    assertThat(jobRecord.getWorker()).isEqualTo(expected.getWorker());
    assertThat(jobRecord.getRetries()).isEqualTo(expected.getRetries());
    assertThat(jobRecord.getType()).isEqualTo(expected.getType());
    assertThat(jobRecord.getCustomHeaders()).isEqualTo(expected.getCustomHeaders());
    assertThat(jobRecord.getPayload()).isEqualTo(expected.getPayload());
  }

  private void assertListedAsActivatable(final long key, final DirectBuffer type) {
    final List<Long> activatableKeys = getActivatableKeys(type);
    assertThat(activatableKeys).contains(key);
  }

  private void refuteListedAsActivatable(final long key, final DirectBuffer type) {
    final List<Long> activatableKeys = getActivatableKeys(type);
    assertThat(activatableKeys).doesNotContain(key);
  }

  private void assertListedAsTimedOut(final long key, final long since) {
    final List<Long> timedOutKeys = getTimedOutKeys(since);
    assertThat(timedOutKeys).contains(key);
  }

  private void refuteListedAsTimedOut(final long key, final long since) {
    final List<Long> timedOutKeys = getTimedOutKeys(since);
    assertThat(timedOutKeys).doesNotContain(key);
  }

  private List<Long> getActivatableKeys(final DirectBuffer type) {
    final List<Long> activatableKeys = new ArrayList<>();

    stateController.forEachActivatableJobs(type, (k, e, c) -> activatableKeys.add(k));
    return activatableKeys;
  }

  private List<Long> getTimedOutKeys(final long since) {
    final List<Long> timedOutKeys = new ArrayList<>();

    stateController.forEachTimedOutEntry(since, (k, e, c) -> timedOutKeys.add(k));
    return timedOutKeys;
  }
}
