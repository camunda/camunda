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
package io.zeebe.engine.processor.workflow.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.state.instance.IncidentState;
import io.zeebe.engine.util.StreamProcessorRule;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.intent.IncidentIntent;
import java.util.List;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class IncidentStreamProcessorTest {

  public StreamProcessorRule envRule = new StreamProcessorRule();
  public IncidentStreamProcessorRule streamProcessorRule = new IncidentStreamProcessorRule(envRule);

  @Rule public RuleChain chain = RuleChain.outerRule(envRule).around(streamProcessorRule);

  @Test
  public void shouldNotCreateIncidentIfNoFailedJob() {
    // given
    final IncidentRecord incidentRecord = new IncidentRecord();
    incidentRecord.setJobKey(1);

    // when
    envRule.writeCommand(IncidentIntent.CREATE, incidentRecord); // trigger incident creation

    // then
    streamProcessorRule.awaitIncidentRejection(IncidentIntent.CREATE);

    final List<TypedRecord<IncidentRecord>> incidentEvents =
        envRule.events().onlyIncidentRecords().collect(Collectors.toList());
    Assertions.assertThat(incidentEvents)
        .extracting(r -> r.getMetadata())
        .extracting(m -> m.getRecordType(), m -> m.getIntent())
        .containsExactly(
            tuple(RecordType.COMMAND, IncidentIntent.CREATE),
            tuple(RecordType.COMMAND_REJECTION, IncidentIntent.CREATE));
  }

  @Test
  public void shouldNotCreateIncidentIfNoFailedToken() {
    // given
    final IncidentRecord incidentRecord = new IncidentRecord();
    incidentRecord.setElementInstanceKey(2);

    // when
    envRule.writeCommand(IncidentIntent.CREATE, incidentRecord); // trigger incident creation

    // then
    streamProcessorRule.awaitIncidentRejection(IncidentIntent.CREATE);

    final List<TypedRecord<IncidentRecord>> incidentEvents =
        envRule.events().onlyIncidentRecords().collect(Collectors.toList());
    Assertions.assertThat(incidentEvents)
        .extracting(r -> r.getMetadata())
        .extracting(m -> m.getRecordType(), m -> m.getIntent())
        .containsExactly(
            tuple(RecordType.COMMAND, IncidentIntent.CREATE),
            tuple(RecordType.COMMAND_REJECTION, IncidentIntent.CREATE));
  }

  @Test
  public void shouldNotResolveIfNoIncident() {
    // given
    final IncidentRecord incidentRecord = new IncidentRecord();
    incidentRecord.setElementInstanceKey(2);

    // when
    envRule.writeCommand(IncidentIntent.RESOLVE, incidentRecord);

    // then
    streamProcessorRule.awaitIncidentRejection(IncidentIntent.RESOLVE);

    final List<TypedRecord<IncidentRecord>> incidentEvents =
        envRule.events().onlyIncidentRecords().collect(Collectors.toList());
    Assertions.assertThat(incidentEvents)
        .extracting(r -> r.getMetadata())
        .extracting(m -> m.getRecordType(), m -> m.getIntent())
        .containsExactly(
            tuple(RecordType.COMMAND, IncidentIntent.RESOLVE),
            tuple(RecordType.COMMAND_REJECTION, IncidentIntent.RESOLVE));
  }

  @Test
  public void shouldRemoveIncidentFromStateOnResolved() {
    // given
    final IncidentState incidentState = streamProcessorRule.getZeebeState().getIncidentState();
    final IncidentRecord incidentRecord = new IncidentRecord();
    incidentRecord.setElementInstanceKey(2);
    incidentState.createIncident(1, incidentRecord);

    // when
    envRule.writeCommand(1, IncidentIntent.RESOLVE, incidentRecord);

    // then
    streamProcessorRule.awaitIncidentInState(IncidentIntent.RESOLVED);
    final IncidentRecord persistedIncident = incidentState.getIncidentRecord(1);
    assertThat(persistedIncident).isNull();
  }
}
