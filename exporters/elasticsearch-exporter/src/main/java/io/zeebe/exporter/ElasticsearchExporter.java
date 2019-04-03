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

import io.zeebe.exporter.ElasticsearchExporterConfiguration.IndexConfiguration;
import io.zeebe.exporter.api.context.Context;
import io.zeebe.exporter.api.context.Controller;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.spi.Exporter;
import io.zeebe.protocol.clientapi.ValueType;
import java.time.Duration;
import org.slf4j.Logger;

public class ElasticsearchExporter implements Exporter {

  public static final String ZEEBE_RECORD_TEMPLATE_JSON = "/zeebe-record-template.json";

  private Logger log;
  private Controller controller;

  private ElasticsearchExporterConfiguration configuration;

  private ElasticsearchClient client;

  private long lastPosition = -1;
  private boolean indexTemplatesCreated;

  @Override
  public void configure(Context context) {
    log = context.getLogger();
    configuration =
        context.getConfiguration().instantiate(ElasticsearchExporterConfiguration.class);
    log.debug("Exporter configured with {}", configuration);
  }

  @Override
  public void open(Controller controller) {
    this.controller = controller;
    client = createClient();
    scheduleDelayedFlush();
    log.info("Exporter opened");
  }

  protected ElasticsearchClient createClient() {
    return new ElasticsearchClient(configuration, log);
  }

  @Override
  public void close() {
    flush();

    try {
      client.close();
    } catch (Exception e) {
      log.warn("Failed to close elasticsearch client", e);
    }
    log.info("Exporter closed");
  }

  @Override
  public void export(Record record) {
    if (!indexTemplatesCreated) {
      createIndexTemplates();
    }

    if (configuration.shouldIndexRecord(record)) {
      client.index(record);
    }

    lastPosition = record.getPosition();

    if (client.shouldFlush()) {
      flush();
    }
  }

  private void flushAndReschedule() {
    flush();
    scheduleDelayedFlush();
  }

  private void scheduleDelayedFlush() {
    controller.scheduleTask(Duration.ofSeconds(configuration.bulk.delay), this::flushAndReschedule);
  }

  private void flush() {
    if (client.flush()) {
      controller.updateLastExportedRecordPosition(lastPosition);
    } else {
      log.warn("Failed to flush bulk completely");
    }
  }

  private void createIndexTemplates() {
    final IndexConfiguration index = configuration.index;

    if (index.createTemplate) {
      createRootIndexTemplate();

      if (index.deployment) {
        createValueIndexTemplate(ValueType.DEPLOYMENT);
      }
      if (index.incident) {
        createValueIndexTemplate(ValueType.INCIDENT);
      }
      if (index.job) {
        createValueIndexTemplate(ValueType.JOB);
      }
      if (index.jobBatch) {
        createValueIndexTemplate(ValueType.JOB_BATCH);
      }
      if (index.message) {
        createValueIndexTemplate(ValueType.MESSAGE);
      }
      if (index.messageSubscription) {
        createValueIndexTemplate(ValueType.MESSAGE_SUBSCRIPTION);
      }
      if (index.variable) {
        createValueIndexTemplate(ValueType.VARIABLE);
      }
      if (index.variableDocument) {
        createValueIndexTemplate(ValueType.VARIABLE_DOCUMENT);
      }
      if (index.workflowInstance) {
        createValueIndexTemplate(ValueType.WORKFLOW_INSTANCE);
      }
      if (index.workflowInstanceCreation) {
        createValueIndexTemplate(ValueType.WORKFLOW_INSTANCE_CREATION);
      }
      if (index.workflowInstanceSubscription) {
        createValueIndexTemplate(ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION);
      }
    }

    indexTemplatesCreated = true;
  }

  private void createRootIndexTemplate() {
    final String templateName = configuration.index.prefix;
    final String filename = ZEEBE_RECORD_TEMPLATE_JSON;
    if (!client.putIndexTemplate(templateName, filename, "-")) {
      log.warn("Put index template {} from file {} was not acknowledged", templateName, filename);
    }
  }

  private void createValueIndexTemplate(final ValueType valueType) {
    if (!client.putIndexTemplate(valueType)) {
      log.warn("Put index template for value type {} was not acknowledged", valueType);
    }
  }
}
