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

import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.util.ZeebeStateRule;
import io.zeebe.protocol.ErrorType;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class IncidentStateTest {

  @Rule public ZeebeStateRule stateRule = new ZeebeStateRule();

  private IncidentState incidentState;
  private ZeebeState zeebeState;

  @Before
  public void setUp() {
    zeebeState = stateRule.getZeebeState();
    incidentState = zeebeState.getIncidentState();
  }

  @Test
  public void shouldCreateWorkflowIncident() {
    // given
    final IncidentRecord expectedRecord = createWorkflowInstanceIncident();

    // when
    incidentState.createIncident(5_000, expectedRecord);

    // then
    final IncidentRecord storedRecord = incidentState.getIncidentRecord(5_000);
    assertIncident(expectedRecord, storedRecord);
  }

  @Test
  public void shouldFindIncidentByElementInstanceKey() {
    // given
    final IncidentRecord expectedRecord = createWorkflowInstanceIncident();
    incidentState.createIncident(5_000, expectedRecord);

    // when
    final long workflowInstanceIncidentKey = incidentState.getWorkflowInstanceIncidentKey(1234);

    // then
    assertThat(workflowInstanceIncidentKey).isEqualTo(5_000);
    final IncidentRecord storedRecord =
        incidentState.getIncidentRecord(workflowInstanceIncidentKey);
    assertIncident(expectedRecord, storedRecord);
  }

  @Test
  public void shouldNotFindIncidentByElementInstanceKey() {
    // given
    final IncidentRecord expectedRecord = createJobIncident();
    incidentState.createIncident(5_000, expectedRecord);

    // when
    final long workflowInstanceIncidentKey = incidentState.getWorkflowInstanceIncidentKey(1234);

    // then
    assertThat(workflowInstanceIncidentKey).isEqualTo(IncidentState.MISSING_INCIDENT);
  }

  @Test
  public void shouldDeleteWorkflowInstanceIncident() {
    // given
    final IncidentRecord expectedRecord = createWorkflowInstanceIncident();
    incidentState.createIncident(5_000, expectedRecord);

    // when
    incidentState.deleteIncident(5_000);

    // then
    final IncidentRecord incidentRecord = incidentState.getIncidentRecord(5_000);
    assertThat(incidentRecord).isNull();

    final long workflowInstanceIncidentKey = incidentState.getWorkflowInstanceIncidentKey(1234);
    assertThat(workflowInstanceIncidentKey).isEqualTo(IncidentState.MISSING_INCIDENT);
  }

  @Test
  public void shouldCreateJobIncident() {
    // given
    final IncidentRecord expectedRecord = createJobIncident();

    // when
    incidentState.createIncident(5_000, expectedRecord);

    // then
    final IncidentRecord storedRecord = incidentState.getIncidentRecord(5_000);
    assertIncident(expectedRecord, storedRecord);
  }

  @Test
  public void shouldFindIncidentByJobKey() {
    // given
    final IncidentRecord expectedRecord = createJobIncident();
    incidentState.createIncident(5_000, expectedRecord);

    // when
    final long jobIncidentKey = incidentState.getJobIncidentKey(1234);

    // then
    assertThat(jobIncidentKey).isEqualTo(5_000);
    final IncidentRecord storedRecord = incidentState.getIncidentRecord(jobIncidentKey);
    assertIncident(expectedRecord, storedRecord);
  }

  @Test
  public void shouldNotFindIncidentByJobKey() {
    // given
    final IncidentRecord expectedRecord = createWorkflowInstanceIncident();
    incidentState.createIncident(5_000, expectedRecord);

    // when
    final long jobIncidentKey = incidentState.getJobIncidentKey(1234);

    // then
    assertThat(jobIncidentKey).isEqualTo(IncidentState.MISSING_INCIDENT);
  }

  @Test
  public void shouldDeleteJobIncident() {
    // given
    final IncidentRecord expectedRecord = createJobIncident();
    incidentState.createIncident(5_000, expectedRecord);

    // when
    incidentState.deleteIncident(5_000);

    // then
    final IncidentRecord incidentRecord = incidentState.getIncidentRecord(5_000);
    assertThat(incidentRecord).isNull();

    final long jobIncidentKey = incidentState.getJobIncidentKey(1234);
    assertThat(jobIncidentKey).isEqualTo(IncidentState.MISSING_INCIDENT);
  }

  @Test
  public void shouldNotOverwritePreviousRecord() {
    // given
    final long key = 1L;
    final IncidentRecord writtenRecord = createJobIncident();

    // when
    incidentState.createIncident(key, writtenRecord);
    writtenRecord.setJobKey(2048);

    // then
    final IncidentRecord readRecord = incidentState.getIncidentRecord(1L);
    assertThat(readRecord.getJobKey()).isNotEqualTo(writtenRecord.getJobKey());
    assertThat(readRecord.getJobKey()).isEqualTo(1234);
    assertThat(writtenRecord.getJobKey()).isEqualTo(2048);
  }

  public IncidentRecord createJobIncident() {
    final IncidentRecord expectedRecord = new IncidentRecord();
    expectedRecord.setJobKey(1234);
    expectedRecord.setErrorMessage("Error because of error");
    expectedRecord.setErrorType(ErrorType.EXTRACT_VALUE_ERROR);
    return expectedRecord;
  }

  public IncidentRecord createWorkflowInstanceIncident() {
    final IncidentRecord expectedRecord = new IncidentRecord();
    expectedRecord.setElementInstanceKey(1234);
    expectedRecord.setBpmnProcessId(wrapString("process"));
    expectedRecord.setElementId(wrapString("process"));
    expectedRecord.setWorkflowInstanceKey(4321);
    expectedRecord.setErrorMessage("Error because of error");
    expectedRecord.setErrorType(ErrorType.EXTRACT_VALUE_ERROR);
    return expectedRecord;
  }

  public void assertIncident(
      final IncidentRecord expectedRecord, final IncidentRecord storedRecord) {

    assertThat(expectedRecord.getJobKey()).isEqualTo(storedRecord.getJobKey());
    assertThat(expectedRecord.getElementInstanceKey())
        .isEqualTo(storedRecord.getElementInstanceKey());
    assertThat(expectedRecord.getBpmnProcessId()).isEqualTo(storedRecord.getBpmnProcessId());
    assertThat(expectedRecord.getElementId()).isEqualTo(storedRecord.getElementId());

    assertThat(expectedRecord.getErrorMessage()).isEqualTo(storedRecord.getErrorMessage());
    assertThat(expectedRecord.getErrorType()).isEqualTo(storedRecord.getErrorType());
  }
}
