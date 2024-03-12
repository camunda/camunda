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
package io.camunda.operate;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Queue;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Metrics {

  // Namespace (prefix) for operate metrics
  public static final String OPERATE_NAMESPACE = "operate.";
  // Timers:
  public static final String TIMER_NAME_QUERY = OPERATE_NAMESPACE + "query";
  public static final String TIMER_NAME_IMPORT_QUERY = OPERATE_NAMESPACE + "import.query";
  public static final String TIMER_NAME_IMPORT_INDEX_QUERY =
      OPERATE_NAMESPACE + "import.index.query";
  public static final String TIMER_NAME_IMPORT_PROCESS_BATCH =
      OPERATE_NAMESPACE + "import.process.batch";
  public static final String TIMER_NAME_IMPORT_TIME = OPERATE_NAMESPACE + "import.time";
  public static final String TIMER_NAME_IMPORT_JOB_SCHEDULED_TIME =
      OPERATE_NAMESPACE + "import.job.scheduled";
  public static final String TIMER_NAME_IMPORT_PROCESSING_DURATION =
      OPERATE_NAMESPACE + "import.processing.duration";
  public static final String TIMER_NAME_IMPORT_POSITION_UPDATE =
      OPERATE_NAMESPACE + "import.position.update";
  public static final String TIMER_NAME_ARCHIVER_QUERY = OPERATE_NAMESPACE + "archiver.query";
  public static final String TIMER_NAME_ARCHIVER_REINDEX_QUERY =
      OPERATE_NAMESPACE + "archiver.reindex.query";
  public static final String TIMER_NAME_ARCHIVER_DELETE_QUERY =
      OPERATE_NAMESPACE + "archiver.delete.query";
  // Counters:
  public static final String COUNTER_NAME_EVENTS_PROCESSED = "events.processed";
  public static final String COUNTER_NAME_EVENTS_PROCESSED_FINISHED_WI =
      "events.processed.finished.process.instances";
  public static final String COUNTER_NAME_COMMANDS = "commands";
  public static final String COUNTER_NAME_ARCHIVED = "archived.process.instances";
  // Gauges:
  public static final String GAUGE_IMPORT_QUEUE_SIZE = "import.queue.size";
  public static final String GAUGE_BPMN_MODEL_COUNT = OPERATE_NAMESPACE + "model.bpmn.count";
  public static final String GAUGE_DMN_MODEL_COUNT = OPERATE_NAMESPACE + "model.dmn.count";
  // Tags
  // -----
  //  Keys:
  public static final String TAG_KEY_NAME = "name",
      TAG_KEY_TYPE = "type",
      TAG_KEY_PARTITION = "partition",
      TAG_KEY_STATUS = "status",
      TAG_KEY_ORGANIZATIONID = "organizationId";
  //  Values:
  public static final String TAG_VALUE_PROCESSINSTANCES = "processInstances",
      TAG_VALUE_CORESTATISTICS = "corestatistics",
      TAG_VALUE_SUCCEEDED = "succeeded",
      TAG_VALUE_FAILED = "failed";
  private static final Logger LOGGER = LoggerFactory.getLogger(Metrics.class);
  private Timer importBatchTimer;
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
    registry.counter(OPERATE_NAMESPACE + name, tags).increment(count);
  }

  public <T> void registerGauge(
      String name, T stateObject, ToDoubleFunction<T> valueFunction, String... tags) {
    Gauge.builder(OPERATE_NAMESPACE + name, stateObject, valueFunction)
        .tags(tags)
        .register(registry);
  }

  public void registerGaugeSupplier(String name, Supplier<Number> gaugeSupplier, String... tags) {
    Gauge.builder(name, gaugeSupplier).tags(tags).register(registry);
  }

  public <E> void registerGaugeQueueSize(String name, Queue<E> queue, String... tags) {
    registerGauge(name, queue, q -> q.size(), tags);
  }

  public Timer getTimer(String name, String... tags) {
    return registry.timer(name, tags);
  }
}
