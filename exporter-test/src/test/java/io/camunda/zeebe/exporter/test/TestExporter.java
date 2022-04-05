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
package io.camunda.zeebe.exporter.test;

import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Context.RecordFilter;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.record.Record;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.jcip.annotations.NotThreadSafe;

@NotThreadSafe
final class TestExporter implements Exporter {
  private final List<Record<?>> exportedRecords = new ArrayList<>();

  private Context context;
  private Controller controller;
  private boolean isClosed;

  @Override
  public void configure(final Context context) {
    this.context = context;
  }

  @Override
  public void open(final Controller controller) {
    this.controller = controller;
  }

  @Override
  public void close() {
    isClosed = true;
  }

  @Override
  public void export(final Record<?> record) {
    exportedRecords.add(record);
  }

  void scheduleTask(final Duration delay, final Runnable task) {
    controller.scheduleCancellableTask(delay, task);
  }

  Context getContext() {
    return context;
  }

  Controller getController() {
    return controller;
  }

  boolean isClosed() {
    return isClosed;
  }

  List<Record<?>> getExportedRecords() {
    return exportedRecords;
  }

  @SuppressWarnings("SameParameterValue")
  void updateLastExportedPosition(final long position) {
    controller.updateLastExportedRecordPosition(position);
  }

  void setRecordFilter(final RecordFilter recordFilter) {
    context.setFilter(recordFilter);
  }
}
