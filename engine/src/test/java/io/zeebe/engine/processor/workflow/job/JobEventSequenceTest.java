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
package io.zeebe.engine.processor.workflow.job;

import static io.zeebe.engine.util.RecordToWrite.command;
import static io.zeebe.engine.util.RecordToWrite.event;
import static io.zeebe.protocol.record.intent.JobBatchIntent.ACTIVATE;
import static io.zeebe.protocol.record.intent.JobIntent.ACTIVATED;
import static io.zeebe.protocol.record.intent.JobIntent.CANCEL;
import static io.zeebe.protocol.record.intent.JobIntent.CANCELED;
import static io.zeebe.protocol.record.intent.JobIntent.CREATE;
import static io.zeebe.protocol.record.intent.JobIntent.CREATED;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordValue;
import io.zeebe.protocol.record.intent.JobBatchIntent;
import io.zeebe.protocol.record.value.JobRecordValue;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;

public class JobEventSequenceTest {

  @Rule public final EngineRule engine = EngineRule.explicitStart();

  @Test
  public void shouldUseJobStateOnCancelCommand() {
    // given
    engine.writeRecords(
        command().job(CREATE),
        event().job(CREATED).causedBy(0),
        command().jobBatch(ACTIVATE),
        command().job(CANCEL));

    // when
    engine.start();

    // then
    final Record<JobRecordValue> canceled =
        RecordingExporter.jobRecords().withIntent(CANCELED).getFirst();

    final Record<JobRecordValue> activated =
        RecordingExporter.jobRecords().withIntent(ACTIVATED).getFirst();

    assertThat(activated.getValue().getDeadline()).isEqualTo(canceled.getValue().getDeadline());

    final List<Record<RecordValue>> records =
        RecordingExporter.records()
            .limit(r -> r.getIntent() == CANCELED)
            .collect(Collectors.toList());
    assertThat(records)
        .extracting(Record::getIntent)
        .containsExactly(
            CREATE, CREATED, ACTIVATE, CANCEL, ACTIVATED, JobBatchIntent.ACTIVATED, CANCELED);
  }
}
