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
package io.camunda.tasklist;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Metrics {

  // Namespace (prefix) for metrics
  public static final String TASKLIST_NAMESPACE = "tasklist.";
  // Timers:
  public static final String TIMER_NAME_QUERY = TASKLIST_NAMESPACE + "query";
  public static final String TIMER_NAME_IMPORT_QUERY = TASKLIST_NAMESPACE + "import.query";
  public static final String TIMER_NAME_IMPORT_INDEX_QUERY =
      TASKLIST_NAMESPACE + "import.index.query";
  public static final String TIMER_NAME_IMPORT_TIME = TASKLIST_NAMESPACE + "import.time";
  public static final String TIMER_NAME_IMPORT_JOB_SCHEDULED_TIME =
      TASKLIST_NAMESPACE + "import.job.scheduled";
  public static final String TIMER_NAME_IMPORT_POSITION_UPDATE =
      TASKLIST_NAMESPACE + "import.position.update";
  public static final String TIMER_NAME_ARCHIVER_QUERY = TASKLIST_NAMESPACE + "archiver.query";
  public static final String TIMER_NAME_ARCHIVER_REINDEX_QUERY =
      TASKLIST_NAMESPACE + "archiver.reindex.query";
  public static final String TIMER_NAME_ARCHIVER_DELETE_QUERY =
      TASKLIST_NAMESPACE + "archiver.delete.query";
  // Counters:
  public static final String COUNTER_NAME_EVENTS_PROCESSED = "events.processed";
  public static final String COUNTER_NAME_EVENTS_PROCESSED_FINISHED_WI =
      "events.processed.finished.process.instances";
  public static final String COUNTER_NAME_COMMANDS = "commands";
  public static final String COUNTER_NAME_ARCHIVED = "archived.process.instances";

  public static final String COUNTER_NAME_CLAIMED_TASKS = "claimed.tasks";
  public static final String COUNTER_NAME_COMPLETED_TASKS = "completed.tasks";
  // Tags
  // -----
  //  Keys:
  public static final String TAG_KEY_NAME = "name",
      TAG_KEY_TYPE = "type",
      TAG_KEY_PARTITION = "partition",
      TAG_KEY_STATUS = "status",
      TAG_KEY_BPMN_PROCESS_ID = "bpmnProcessId",
      TAG_KEY_FLOW_NODE_ID = "flowNodeId",
      TAG_KEY_USER_ID = "userId",
      TAG_KEY_ORGANIZATION_ID = "organizationId";

  //  Values:
  public static final String TAG_VALUE_PROCESSINSTANCES = "processInstances",
      TAG_VALUE_CORESTATISTICS = "corestatistics",
      TAG_VALUE_SUCCEEDED = "succeeded",
      TAG_VALUE_FAILED = "failed";
  private static final Logger LOGGER = LoggerFactory.getLogger(Metrics.class);
  @Autowired private MeterRegistry registry;

  /**
   * Record counts for given name and tags. Tags are further attributes that gives the possibility
   * to categorize the counter. They will be given as varargs key value pairs. For example:
   * "type":"incident". Original documentation for tags: <a
   * href="https://micrometer.io/docs/concepts#_tag_naming">Tags naming</a>
   *
   * @param name - Name of counter
   * @param count - Number to count
   * @param tags - key value pairs of tags as Strings - The size of tags varargs must be even.
   */
  public void recordCounts(String name, long count, String... tags) {
    registry.counter(TASKLIST_NAMESPACE + name, tags).increment(count);
  }

  public Timer getTimer(String name, String... tags) {
    return registry.timer(name, tags);
  }
}
