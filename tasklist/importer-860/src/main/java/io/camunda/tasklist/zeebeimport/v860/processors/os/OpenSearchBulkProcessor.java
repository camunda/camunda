/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.zeebeimport.v860.processors.os;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.Metrics;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.util.OpenSearchUtil;
import io.camunda.tasklist.zeebe.ImportValueType;
import io.camunda.tasklist.zeebeimport.ImportBatch;
import io.camunda.tasklist.zeebeimport.os.AbstractImportBatchProcessorOpenSearch;
import io.camunda.tasklist.zeebeimport.v860.record.RecordImpl;
import io.camunda.tasklist.zeebeimport.v860.record.value.JobRecordValueImpl;
import io.camunda.tasklist.zeebeimport.v860.record.value.ProcessInstanceRecordValueImpl;
import io.camunda.tasklist.zeebeimport.v860.record.value.UserTaskRecordValueImpl;
import io.camunda.tasklist.zeebeimport.v860.record.value.VariableRecordValueImpl;
import io.camunda.tasklist.zeebeimport.v860.record.value.deployment.DeployedProcessImpl;
import io.camunda.tasklist.zeebeimport.v860.record.value.deployment.FormRecordImpl;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class OpenSearchBulkProcessor extends AbstractImportBatchProcessorOpenSearch {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchBulkProcessor.class);

  @Autowired
  private ProcessInstanceZeebeRecordProcessorOpenSearch processInstanceZeebeRecordProcessor;

  @Autowired private VariableZeebeRecordProcessorOpenSearch variableZeebeRecordProcessor;

  @Autowired private JobZeebeRecordProcessorOpenSearch jobZeebeRecordProcessor;

  @Autowired private ProcessZeebeRecordProcessorOpenSearch processZeebeRecordProcessor;

  @Autowired private FormZeebeRecordProcessorOpenSearch formZeebeRecordProcessor;

  @Autowired private UserTaskZeebeRecordProcessorOpenSearch userTaskZeebeRecordProcessor;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private Metrics metrics;

  @Override
  protected void processZeebeRecords(
      final ImportBatch importBatch, final List<BulkOperation> operations)
      throws PersistenceException {

    final JavaType valueType =
        objectMapper
            .getTypeFactory()
            .constructParametricType(
                RecordImpl.class, getRecordValueClass(importBatch.getImportValueType()));

    final List<Record> zeebeRecords =
        OpenSearchUtil.mapSearchHits(importBatch.getHits(), objectMapper, valueType);

    final ImportValueType importValueType = importBatch.getImportValueType();

    LOGGER.debug("Writing [{}] Zeebe records to OpenSearch", zeebeRecords.size());
    for (final Record record : zeebeRecords) {
      switch (importValueType) {
        case PROCESS_INSTANCE:
          processInstanceZeebeRecordProcessor.processProcessInstanceRecord(record, operations);
          break;
        case VARIABLE:
          variableZeebeRecordProcessor.processVariableRecord(record, operations);
          break;
        case JOB:
          jobZeebeRecordProcessor.processJobRecord(record, operations);
          break;
        case PROCESS:
          // deployment records can be processed one by one
          processZeebeRecordProcessor.processDeploymentRecord(record, operations);
          break;
        case FORM:
          // form records can be processed one by one
          formZeebeRecordProcessor.processFormRecord(record, operations);
          break;
        case USER_TASK:
          userTaskZeebeRecordProcessor.processUserTaskRecord(record, operations);
          break;
        default:
          LOGGER.debug("Default case triggered for type {}", importValueType);
          break;
      }
    }
    recordRecordImportTime(zeebeRecords);
  }

  private void recordRecordImportTime(final List<Record> zeebeRecords) {
    final var currentTime = OffsetDateTime.now().toInstant().toEpochMilli();
    zeebeRecords.forEach(
        record ->
            metrics
                .getTimer(
                    Metrics.TIMER_NAME_IMPORT_TIME,
                    Metrics.TAG_KEY_TYPE,
                    record.getValueType().toString(),
                    Metrics.TAG_KEY_PARTITION,
                    String.valueOf(record.getPartitionId()))
                .record(currentTime - record.getTimestamp(), TimeUnit.MILLISECONDS));
  }

  protected Class<? extends RecordValue> getRecordValueClass(
      final ImportValueType importValueType) {
    switch (importValueType) {
      case PROCESS_INSTANCE:
        return ProcessInstanceRecordValueImpl.class;
      case JOB:
        return JobRecordValueImpl.class;
      case VARIABLE:
        return VariableRecordValueImpl.class;
      case PROCESS:
        return DeployedProcessImpl.class;
      case FORM:
        return FormRecordImpl.class;
      case USER_TASK:
        return UserTaskRecordValueImpl.class;
      default:
        throw new TasklistRuntimeException(
            String.format("No value type class found for: %s", importValueType));
    }
  }

  @Override
  public String getZeebeVersion() {
    return "8.6";
  }
}
