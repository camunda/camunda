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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Configuration;
import io.camunda.zeebe.exporter.api.context.Context.RecordFilter;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.exporter.api.context.ScheduledTask;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.jcip.annotations.NotThreadSafe;
import org.agrona.LangUtil;
import org.agrona.collections.MutableLong;

/**
 * TODO: evaluate if this is really useful. If not, then we might just move the other classes to
 * test-util and leave it at that. Honestly this is probably over engineered, but it is kind of nice
 * to tie things together, as most of the time you really don't care about the individual
 * components.
 *
 * <p>A test harness to simplify testing {@link Exporter} instances in a synchronous context,
 * without requiring a complete broker.
 *
 * <h2>Lifecycle</h2>
 *
 * <p>The harness follows the same lifecycle as the normal exporter. You <em>must</em> use it
 * accordingly, and it will fail when the lifecycle is bypassed.
 *
 * <p>This means it follows these rules:
 *
 * <ul>
 *   <li>the exporter must be closed before it can be configured
 *   <li>the exporter must be configured before it is opened
 *   <li>the exporter must be opened before it can export
 *   <li>the exporter must be opened or configured before it is closed
 * </ul>
 *
 * <p>NOTE: the harness starts with the exporter in a closed state.
 *
 * <p>NOTE: if the exporter is already configured, you can omit the call to {@link #open()} and skip
 * directly to {@link #export(Record)}, which will always open the exporter if it wasn't done yet.
 *
 * <h2>Scheduling tasks</h2>
 *
 * <p>As everything runs in a synchronous context, scheduled tasks are not run automatically.
 * Instead, they are simply collected when scheduled. To execute them, you must tick time manually
 * by calling {@link #runScheduledTasks(Duration)} with the appropriate duration. Time will be
 * increased by the given relative amount, and the scheduled tasks will be run sequentially,
 * synchronously, and ordered by their deadline. Note that if two tasks where supposed to be
 * executed at the same time, their order is undefined.
 */
@NotThreadSafe
public final class ExporterTestHarness {
  private final ExporterTestController controller = new ExporterTestController();
  private final ExporterTestContext context = new ExporterTestContext();
  private final ProtocolFactory recordFactory = new ProtocolFactory();

  private final Exporter exporter;

  private State exporterState = State.INITIAL;

  /**
   * @param exporter the exporter to be tested
   */
  public ExporterTestHarness(final Exporter exporter) {
    this.exporter = Objects.requireNonNull(exporter, "must specify an exporter");
  }

  /**
   * @param exporter the exporter to be tested
   */
  public <T> ExporterTestHarness(
      final Exporter exporter, final String id, @Nullable final T config) {
    this.exporter = Objects.requireNonNull(exporter, "must specify an exporter");

    // sneakily configure
    try {
      configure(id, config);
    } catch (final Exception e) {
      LangUtil.rethrowUnchecked(e);
    }
  }

  /**
   * Convenience method which will substitute a prepared configuration instance such that it is
   * always returned by a call to {@link Configuration#instantiate(Class)}
   *
   * <p>NOTE: in this case, {@link Configuration#getArguments()} always returns an empty map.
   *
   * @param id the exporter ID
   * @param config new return value of {@link Configuration#instantiate(Class)}
   * @param <T> type of the configuration class
   * @throws IllegalStateException if the exporter is already opened
   */
  public <T> void configure(final String id, @Nullable final T config) throws Exception {
    if (exporterState == State.OPENED) {
      throw new IllegalStateException(
          String.format(
              "Expected to configure exporter, but the exporter is already configured and in state '%s'",
              exporterState));
    }

    exporter.configure(
        context.setConfiguration(
            new ExporterTestConfiguration<>(
                Objects.requireNonNull(id, "must specify an exporter ID"), config)));
    exporterState = State.CONFIGURED;
  }

  /**
   * Convenience method which will substitute a prepared configuration instance such that it is
   * always returned by a call to {@link Configuration#instantiate(Class)}
   *
   * <p>NOTE: in this case, {@link Configuration#getArguments()} always returns an empty map.
   *
   * @param id the exporter ID
   * @param configurationSupplier a function which takes in the configured map of arguments, and
   *     returns an instantiated configuration of type {@code T}
   * @param <T> type of the configuration class
   * @throws IllegalStateException if the exporter is already opened
   */
  public <T> void configure(
      final String id, final Function<Map<String, Object>, T> configurationSupplier)
      throws Exception {
    if (exporterState == State.OPENED) {
      throw new IllegalStateException(
          String.format(
              "Expected to configure exporter, but the exporter is already configured and in state '%s'",
              exporterState));
    }

    exporter.configure(
        context.setConfiguration(new ExporterTestConfiguration<>(id, configurationSupplier)));
    exporterState = State.CONFIGURED;
  }

  /**
   * Resets scheduled tasks and opens the exporter via {@link Exporter#open(Controller)}.
   *
   * <p>NOTE: if closing/opening the exporter here several times, the start position will be the
   * same as the end position when the exporter was last closed.
   *
   * @throws IllegalStateException if the exporter was not configured yet, or is already opened
   */
  public void open() {
    if (exporterState != State.CONFIGURED && exporterState != State.CLOSED) {
      throw new IllegalStateException(
          String.format(
              "Expected to open exporter, but the exporter was not yet configured; it's currently in state '%s'",
              exporterState));
    }

    controller.resetScheduledTasks();
    exporter.open(controller);
    exporterState = State.OPENED;
  }

  /**
   * Closes the exporter via {@link Exporter#close()}.
   *
   * @throws IllegalStateException if the exporter is neither configured nor opened, i.e. if there's
   *     nothing to be closed
   */
  public void close() {
    if (exporterState != State.CONFIGURED && exporterState != State.OPENED) {
      throw new IllegalStateException(
          String.format(
              "Expected to close exporter, but the exporter was neither configured nor opened; it's currently in state '%s'",
              exporterState));
    }

    exporter.close();
    exporterState = State.CLOSED;
  }

  /**
   * Exports a single record to the exporter. The record is not patched but is exported as is.
   *
   * <p>NOTE: if the exporter is not opened yet, it will attempt to do so via {@link #open()}.
   */
  public void export(final Record<RecordValue> record) {
    if (exporterState != State.OPENED) {
      open();
    }

    Objects.requireNonNull(record, "must specify a record");
    exporter.export(record);
  }

  /**
   * Exports a single, randomly generated record to the exporter. The record is patched before being
   * exported to ensure it has the correct partition ID.
   *
   * <p>NOTE: if the exporter is not opened yet, it will attempt to do so via {@link #open()}.
   */
  public void export() {
    export(recordFactory.generateRecord(this::patchRecord));
  }

  /**
   * Exports exactly {@code count}, randomly generated records. The data is not expected to be
   * semantically correct, however the partition ID will be correct, the positions will be
   * monotonically increasing, and the timestamps will be ordered as well.
   *
   * @param count the number of records to generate and export
   * @throws IllegalStateException if the exporter is not opened and cannot be opened
   */
  public List<Record<RecordValue>> export(final long startPosition, final int count) {
    if (exporterState != State.OPENED) {
      open();
    }

    final var position = new MutableLong(startPosition);
    final var records =
        recordFactory
            .generateRecords(b -> patchRecord(b).withPosition(position.getAndIncrement()))
            .limit(count)
            .collect(Collectors.toList());

    records.forEach(exporter::export);
    return records;
  }

  /**
   * Runs all scheduled tasks which should be expired if {@param elapsed} time has passed since the
   * last time this was called.
   *
   * @param elapsed time to elapse
   */
  public void runScheduledTasks(final Duration elapsed) {
    controller.runScheduledTasks(
        Objects.requireNonNull(elapsed, "must specify an elapsed duration"));
  }

  public List<ScheduledTask> getScheduledTasks() {
    return Collections.unmodifiableList(controller.getScheduledTasks());
  }

  public long getLastExportedRecordPosition() {
    return controller.getPosition();
  }

  @Nullable
  @CheckForNull
  public RecordFilter getRecordFilter() {
    return context.getRecordFilter();
  }

  private ImmutableRecord.Builder<RecordValue> patchRecord(
      final ImmutableRecord.Builder<RecordValue> builder) {
    return builder
        .withTimestamp(System.currentTimeMillis())
        .withPartitionId(context.getPartitionId());
  }

  private enum State {
    // initial, non configured state
    INITIAL,
    // exporter has a known configuration and configure was called
    CONFIGURED,
    // exporter has a known configuration and open was called
    OPENED,
    // exporter has a known configuration and closed was called
    CLOSED
  }
}
