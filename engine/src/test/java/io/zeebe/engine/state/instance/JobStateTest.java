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
package io.zeebe.engine.state.instance;

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.instance.JobState.State;
import io.zeebe.engine.util.ZeebeStateRule;
import io.zeebe.msgpack.value.DocumentValue;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.test.util.BufferAssert;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class JobStateTest {
  @Rule public ZeebeStateRule stateRule = new ZeebeStateRule();

  private JobState jobState;
  private ZeebeState zeebeState;

  @Before
  public void setUp() {
    zeebeState = stateRule.getZeebeState();
    jobState = zeebeState.getJobState();
  }

  @Test
  public void shouldCreateJobEntry() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    jobState.create(key, jobRecord);

    // then
    Assertions.assertThat(jobState.exists(key)).isTrue();
    assertJobState(key, State.ACTIVATABLE);
    assertJobRecordIsEqualTo(jobState.getJob(key), jobRecord);
    assertListedAsActivatable(key, jobRecord.getType());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
  }

  @Test
  public void shouldActivateJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    jobState.create(key, jobRecord);
    jobState.activate(key, jobRecord);

    // then
    Assertions.assertThat(jobState.exists(key)).isTrue();
    assertJobState(key, State.ACTIVATED);
    assertJobRecordIsEqualTo(jobState.getJob(key), jobRecord);
    refuteListedAsActivatable(key, jobRecord.getType());
    assertListedAsTimedOut(key, jobRecord.getDeadline() + 1);
  }

  @Test
  public void shouldTimeoutJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    jobState.create(key, jobRecord);
    jobState.activate(key, jobRecord);
    jobState.timeout(key, jobRecord);

    // then
    Assertions.assertThat(jobState.exists(key)).isTrue();
    assertJobState(key, State.ACTIVATABLE);
    assertJobRecordIsEqualTo(jobState.getJob(key), jobRecord);
    assertListedAsActivatable(key, jobRecord.getType());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
  }

  @Test
  public void shouldNeverPersistJobVariables() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    final List<BiConsumer<Long, JobRecord>> stateUpdates =
        Arrays.asList(
            jobState::create,
            jobState::activate,
            jobState::timeout,
            jobState::activate,
            jobState::fail);

    // when job state is updated then the variables is not persisted
    for (BiConsumer<Long, JobRecord> stateUpdate : stateUpdates) {
      jobRecord.setVariables(MsgPackUtil.asMsgPack("foo", "bar"));
      stateUpdate.accept(key, jobRecord);
      final DirectBuffer variables = jobState.getJob(key).getVariables();
      BufferAssert.assertThatBuffer(variables).isEqualTo(DocumentValue.EMPTY_DOCUMENT);
    }
  }

  @Test
  public void shouldDeleteActivatableJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    jobState.create(key, jobRecord);
    jobState.delete(key, jobRecord);

    // then
    Assertions.assertThat(jobState.exists(key)).isFalse();
    Assertions.assertThat(jobState.isInState(key, State.NOT_FOUND)).isTrue();
    Assertions.assertThat(jobState.getJob(key)).isNull();
    refuteListedAsActivatable(key, jobRecord.getType());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
  }

  @Test
  public void shouldDeleteActivatedJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    jobState.create(key, jobRecord);
    jobState.activate(key, jobRecord);
    jobState.delete(key, jobRecord);

    // then
    Assertions.assertThat(jobState.exists(key)).isFalse();
    Assertions.assertThat(jobState.isInState(key, State.NOT_FOUND)).isTrue();
    Assertions.assertThat(jobState.getJob(key)).isNull();
    refuteListedAsActivatable(key, jobRecord.getType());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
  }

  @Test
  public void shouldDeleteFailedJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    jobState.create(key, jobRecord);
    jobState.activate(key, jobRecord);
    jobState.fail(key, jobRecord.setRetries(0));
    jobState.delete(key, jobRecord);

    // then
    Assertions.assertThat(jobState.exists(key)).isFalse();
    Assertions.assertThat(jobState.isInState(key, State.NOT_FOUND)).isTrue();
    Assertions.assertThat(jobState.getJob(key)).isNull();
    refuteListedAsActivatable(key, jobRecord.getType());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
  }

  @Test
  public void shouldFailJobWithRetriesLeft() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord().setRetries(1);

    // when
    jobState.create(key, jobRecord);
    jobState.activate(key, jobRecord);
    jobState.fail(key, jobRecord);

    // then
    Assertions.assertThat(jobState.exists(key)).isTrue();
    assertJobState(key, State.ACTIVATABLE);
    assertJobRecordIsEqualTo(jobState.getJob(key), jobRecord);
    assertListedAsActivatable(key, jobRecord.getType());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
  }

  @Test
  public void shouldFailJobWithNoRetriesLeft() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord().setRetries(0);

    // when
    jobState.create(key, jobRecord);
    jobState.activate(key, jobRecord);
    jobState.fail(key, jobRecord);

    // then
    Assertions.assertThat(jobState.exists(key)).isTrue();
    assertJobState(key, State.FAILED);
    assertJobRecordIsEqualTo(jobState.getJob(key), jobRecord);
    refuteListedAsActivatable(key, jobRecord.getType());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
  }

  @Test
  public void shouldResolveJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    jobState.create(key, jobRecord);
    jobState.activate(key, jobRecord);
    jobState.fail(key, jobRecord.setRetries(0));
    jobState.resolve(key, jobRecord);

    // then
    Assertions.assertThat(jobState.exists(key)).isTrue();
    assertJobState(key, State.ACTIVATABLE);
    assertJobRecordIsEqualTo(jobState.getJob(key), jobRecord);
    assertListedAsActivatable(key, jobRecord.getType());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
  }

  @Test
  public void shouldListTimedOutEntriesInOrder() {
    // given
    createAndActivateJobRecord(1, newJobRecord().setDeadline(1L));
    createAndActivateJobRecord(2, newJobRecord().setDeadline(256L));
    jobState.create(5, newJobRecord().setDeadline(512L));
    createAndActivateJobRecord(3, newJobRecord().setDeadline(65536L));
    createAndActivateJobRecord(4, newJobRecord().setDeadline(4294967296L));

    // when
    final List<Long> jobKeys = getTimedOutKeys(32768L);

    // then
    assertThat(jobKeys).hasSize(2);
    assertThat(jobKeys).containsExactly(1L, 2L);
  }

  @Test
  public void shouldOnlyIterateOverTimedoutWhileTrue() {
    // given
    createAndActivateJobRecord(1, newJobRecord().setDeadline(1L));
    createAndActivateJobRecord(2, newJobRecord().setDeadline(256L));
    createAndActivateJobRecord(3, newJobRecord().setDeadline(512L));
    createAndActivateJobRecord(4, newJobRecord().setDeadline(65536L));
    createAndActivateJobRecord(5, newJobRecord().setDeadline(4294967296L));

    // when
    final List<Long> timedOutKeys = new ArrayList<>();
    final long since = 65536L;
    jobState.forEachTimedOutEntry(
        since,
        (k, e) -> {
          timedOutKeys.add(k);
          return k.longValue() < 3;
        });

    // then
    assertThat(timedOutKeys).hasSize(3);
    assertThat(timedOutKeys).containsExactly(1L, 2L, 3L);
  }

  @Test
  public void shouldDoNothingIfNotTimedOutJobs() {
    // given
    jobState.create(5, newJobRecord().setDeadline(512L));
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
    jobState.create(key, jobRecord);

    // then
    Assertions.assertThat(jobState.exists(key)).isTrue();
    Assertions.assertThat(jobState.exists(key + 1)).isFalse();
  }

  @Test
  public void shouldListActivatableJobsForTypeInOrder() {
    // given
    final DirectBuffer type = wrapString("test");
    jobState.create(1, newJobRecord().setType("tes"));
    jobState.create(256L, newJobRecord().setType(type));
    createAndActivateJobRecord(512, newJobRecord().setType(type));
    jobState.create(65536L, newJobRecord().setType(type));
    jobState.create(4294967296L, newJobRecord().setType("test-other"));

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
    jobState.create(256L, newJobRecord().setType("other"));

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
    Assertions.assertThat(jobState.getJob(key)).isNull();
  }

  @Test
  public void shouldReturnCorrectJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord().setType("test");

    // when
    jobState.create(key, jobRecord);
    jobState.create(key + 1, newJobRecord().setType("other"));

    // then
    final JobRecord savedJob = jobState.getJob(key);
    assertJobRecordIsEqualTo(savedJob, jobRecord);
    assertThat(BufferUtil.bufferAsString(savedJob.getType())).isEqualTo("test");
  }

  @Test
  public void testInvariants() {
    final JobRecord jobWithoutType = newJobRecord().setType(new UnsafeBuffer(0, 0));
    final JobRecord jobWithoutDeadline = newJobRecord().setDeadline(0L);

    // create
    assertThatThrownBy(() -> jobState.create(1L, jobWithoutType))
        .hasStackTraceContaining("type must not be empty");

    // activate
    assertThatThrownBy(() -> jobState.activate(1L, jobWithoutType))
        .hasMessage("type must not be empty");
    assertThatThrownBy(() -> jobState.activate(1L, jobWithoutDeadline))
        .hasMessage("deadline must be greater than 0");

    // fail
    assertThatThrownBy(() -> jobState.fail(1L, jobWithoutType))
        .hasMessage("type must not be empty");
    assertThatThrownBy(() -> jobState.fail(1L, jobWithoutDeadline))
        .hasMessage("deadline must be greater than 0");

    // resolve
    assertThatThrownBy(() -> jobState.resolve(1L, jobWithoutType))
        .hasStackTraceContaining("type must not be empty");

    // timeout
    assertThatThrownBy(() -> jobState.timeout(1L, jobWithoutType))
        .hasMessage("type must not be empty");
    assertThatThrownBy(() -> jobState.timeout(1L, jobWithoutDeadline))
        .hasMessage("deadline must be greater than 0");

    // delete
    assertThatThrownBy(() -> jobState.delete(1L, jobWithoutType))
        .hasStackTraceContaining("type must not be empty");

    // should not throw any exception
    jobState.activate(1L, newJobRecord());
    jobState.delete(1L, jobWithoutDeadline);
  }

  @Test
  public void shouldNotOverwritePreviousRecord() {
    // given
    final long key = 1L;
    final JobRecord writtenRecord = newJobRecord();

    // when
    jobState.create(key, writtenRecord);
    writtenRecord.setType("foo");

    // then
    final JobRecord readRecord = jobState.getJob(key);
    assertThat(readRecord.getType()).isNotEqualTo(writtenRecord.getType());
    assertThat(readRecord.getType()).isEqualTo(BufferUtil.wrapString("test"));
    assertThat(writtenRecord.getType()).isEqualTo(BufferUtil.wrapString("foo"));
  }

  private void createAndActivateJobRecord(final long key, final JobRecord record) {
    jobState.create(key, record);
    jobState.activate(key, record);
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

    Assertions.assertThat(jobState.isInState(key, state)).isTrue();
    Assertions.assertThat(others).noneMatch(other -> jobState.isInState(key, other));
  }

  private void assertJobRecordIsEqualTo(final JobRecord jobRecord, final JobRecord expected) {
    assertThat(jobRecord.getDeadline()).isEqualTo(expected.getDeadline());
    assertThat(jobRecord.getWorker()).isEqualTo(expected.getWorker());
    assertThat(jobRecord.getRetries()).isEqualTo(expected.getRetries());
    assertThat(jobRecord.getType()).isEqualTo(expected.getType());
    assertThat(jobRecord.getCustomHeaders()).isEqualTo(expected.getCustomHeaders());
    assertThat(jobRecord.getVariables()).isEqualTo(expected.getVariables());
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

    jobState.forEachActivatableJobs(type, (k, e) -> activatableKeys.add(k));
    return activatableKeys;
  }

  private List<Long> getTimedOutKeys(final long since) {
    final List<Long> timedOutKeys = new ArrayList<>();

    jobState.forEachTimedOutEntry(since, (k, e) -> timedOutKeys.add(k));
    return timedOutKeys;
  }
}
