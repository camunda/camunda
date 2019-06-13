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
package io.zeebe.engine.util.client;

import io.zeebe.engine.util.StreamProcessorRule;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.WorkflowInstanceRecordValue;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceCreationIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import java.util.function.Function;

public class WorkflowInstanceClient {

  private final StreamProcessorRule environmentRule;

  public WorkflowInstanceClient(StreamProcessorRule environmentRule) {
    this.environmentRule = environmentRule;
  }

  public WorkflowInstanceCreationClient ofBpmnProcessId(String bpmnProcessId) {
    return new WorkflowInstanceCreationClient(environmentRule, bpmnProcessId);
  }

  public static class WorkflowInstanceCreationClient {

    private final StreamProcessorRule environmentRule;
    private final WorkflowInstanceCreationRecord workflowInstanceCreationRecord;

    public WorkflowInstanceCreationClient(
        StreamProcessorRule environmentRule, String bpmnProcessId) {
      this.environmentRule = environmentRule;
      this.workflowInstanceCreationRecord = new WorkflowInstanceCreationRecord();
      workflowInstanceCreationRecord.setBpmnProcessId(bpmnProcessId);
    }

    public WorkflowInstanceCreationClient withVariables(Map<String, Object> variables) {
      workflowInstanceCreationRecord.setVariables(MsgPackUtil.asMsgPack(variables));
      return this;
    }

    public WorkflowInstanceCreationClient withVariables(String variables) {
      workflowInstanceCreationRecord.setVariables(MsgPackUtil.asMsgPack(variables));
      return this;
    }

    public WorkflowInstanceCreationClient withVariable(String key, Object value) {
      workflowInstanceCreationRecord.setVariables(MsgPackUtil.asMsgPack(key, value));
      return this;
    }

    public long create() {
      final long position =
          environmentRule.writeCommand(
              WorkflowInstanceCreationIntent.CREATE, workflowInstanceCreationRecord);

      return RecordingExporter.workflowInstanceCreationRecords()
          .withIntent(WorkflowInstanceCreationIntent.CREATED)
          .withSourceRecordPosition(position)
          .getFirst()
          .getValue()
          .getInstanceKey();
    }
  }

  public ExistingInstanceClient withInstanceKey(long workflowInstanceKey) {
    return new ExistingInstanceClient(environmentRule, workflowInstanceKey);
  }

  public static class ExistingInstanceClient {

    public static final Function<Long, Record<WorkflowInstanceRecordValue>> SUCCESS_EXPECTATION =
        (workflowInstanceKey) ->
            RecordingExporter.workflowInstanceRecords()
                .withRecordKey(workflowInstanceKey)
                .withIntent(WorkflowInstanceIntent.ELEMENT_TERMINATED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .getFirst();

    public static final Function<Long, Record<WorkflowInstanceRecordValue>> REJECTION_EXPECTATION =
        (workflowInstanceKey) ->
            RecordingExporter.workflowInstanceRecords()
                .onlyCommandRejections()
                .withIntent(WorkflowInstanceIntent.CANCEL)
                .withRecordKey(workflowInstanceKey)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .getFirst();

    private static final int DEFAULT_PARTITION = -1;
    private final StreamProcessorRule environmentRule;
    private final long workflowInstanceKey;

    private int partition = DEFAULT_PARTITION;
    private Function<Long, Record<WorkflowInstanceRecordValue>> expectation = SUCCESS_EXPECTATION;

    public ExistingInstanceClient(StreamProcessorRule environmentRule, long workflowInstanceKey) {
      this.environmentRule = environmentRule;
      this.workflowInstanceKey = workflowInstanceKey;
    }

    public ExistingInstanceClient onPartition(int partition) {
      this.partition = partition;
      return this;
    }

    public ExistingInstanceClient expectRejection() {
      expectation = REJECTION_EXPECTATION;
      return this;
    }

    public Record<WorkflowInstanceRecordValue> cancel() {
      if (partition == DEFAULT_PARTITION) {
        partition =
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .getFirst()
                .getMetadata()
                .getPartitionId();
      }

      environmentRule.writeCommandOnPartition(
          partition,
          workflowInstanceKey,
          WorkflowInstanceIntent.CANCEL,
          new WorkflowInstanceRecord().setWorkflowInstanceKey(workflowInstanceKey));

      return expectation.apply(workflowInstanceKey);
    }
  }
}
