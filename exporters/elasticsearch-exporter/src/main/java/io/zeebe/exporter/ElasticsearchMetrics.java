/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.exporter;

import io.prometheus.client.Histogram;

public class ElasticsearchMetrics {

  private static final Histogram FLUSH_DURATION =
      Histogram.build()
          .namespace("zeebe_elasticsearch_exporter")
          .name("flush_duration_seconds")
          .help("Flush duration of bulk exporters in seconds")
          .labelNames("partition")
          .register();

  private static final Histogram BULK_SIZE =
      Histogram.build()
          .namespace("zeebe_elasticsearch_exporter")
          .name("bulk_size")
          .help("Exporter bulk size")
          .buckets(10, 100, 1_000, 10_000, 100_000)
          .labelNames("partition")
          .register();

  private final String partitionIdLabel;

  public ElasticsearchMetrics(int partitionId) {
    this.partitionIdLabel = String.valueOf(partitionId);
  }

  public Histogram.Timer measureFlushDuration() {
    return FLUSH_DURATION.labels(partitionIdLabel).startTimer();
  }

  public void recordBulkSize(int bulkSize) {
    BULK_SIZE.labels(partitionIdLabel).observe(bulkSize);
  }
}
