/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.exporter.api.context.ScheduledTask;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.util.CloseableSilently;
import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncExporterWrapper implements Exporter {
  private static final Logger LOGGER = LoggerFactory.getLogger(AsyncExporterWrapper.class);
  private static final Duration POSITION_UPDATE_DELAY = Duration.ofSeconds(2L);
  private final Exporter exporter;
  private Runner runner;
  private Controller controller;
  private ScheduledTask updateTask;

  public AsyncExporterWrapper(final Exporter exporter) {
    this.exporter = exporter;
  }

  @Override
  public void configure(final Context context) throws Exception {
    exporter.configure(context);
  }

  @Override
  public void open(final Controller controller) {
    runner = new Runner();
    exporter.open(new ControllerWrapper(controller, runner));
    this.controller = controller;
    runner.start();
    updateTask =
        this.controller.scheduleCancellableTask(
            POSITION_UPDATE_DELAY, this::updatePositionAndReschedule);
  }

  @Override
  public void close() {
    exporter.close();
    if (runner != null) {
      runner.close();
      runner = null;
    }
    if (updateTask != null) {
      updateTask.cancel();
    }
  }

  @Override
  public void export(final Record<?> record) {
    runner.updatePosition();
    runner.recordQueue.add(record);
  }

  @Override
  public void purge() throws Exception {
    exporter.purge();
  }

  private void updatePositionAndReschedule() {
    if (runner != null) {
      runner.updatePosition();
    }
    updateTask =
        controller.scheduleCancellableTask(
            POSITION_UPDATE_DELAY, this::updatePositionAndReschedule);
  }

  record PositionUpdate(long position, byte[] metadata) {}

  record Event(long deadlineMillis, Runnable task) {}

  static class ControllerWrapper implements Controller {
    private final Controller controller;
    private final Runner runner;

    public ControllerWrapper(final Controller controller, final Runner runner) {
      this.controller = controller;
      this.runner = runner;
    }

    @Override
    public void updateLastExportedRecordPosition(final long position) {
      runner.positionUpdates.add(new PositionUpdate(position, null));
    }

    @Override
    public void updateLastExportedRecordPosition(final long position, final byte[] metadata) {
      runner.positionUpdates.add(new PositionUpdate(position, metadata));
    }

    @Override
    public long getLastExportedRecordPosition() {
      throw new UnsupportedOperationException("Not supported in async exporter wrapper");
    }

    @Override
    public ScheduledTask scheduleCancellableTask(final Duration delay, final Runnable task) {
      final var deadlineMillis = System.currentTimeMillis() + delay.toMillis();
      runner.events.add(new Event(deadlineMillis, task));
      // TODO support cancelling
      return () -> {
        /*nothing*/
      };
    }

    @Override
    public Optional<byte[]> readMetadata() {
      return controller.readMetadata();
    }

    @Override
    public boolean requestReplay(final long fromPosition) {
      throw new UnsupportedOperationException("Not supported in async exporter wrapper");
    }
  }

  // TODO really need to ensure we guard against exceptions etc
  // e.g. probably need to do a peek and remove when using queues
  class Runner implements Runnable, CloseableSilently {
    private final BlockingQueue<Record<?>> recordQueue = new ArrayBlockingQueue<>(10_000);
    private final Queue<PositionUpdate> positionUpdates = new ConcurrentLinkedQueue<>();
    private final PriorityBlockingQueue<Event> events =
        new PriorityBlockingQueue<>(11, Comparator.comparing(Event::deadlineMillis));

    private volatile boolean running = true;

    public void updatePosition() {
      while (!positionUpdates.isEmpty()) {
        final var update = positionUpdates.poll();
        if (update.metadata() != null) {
          controller.updateLastExportedRecordPosition(update.position(), update.metadata());
        } else {
          controller.updateLastExportedRecordPosition(update.position());
        }
      }
    }

    private void runEvents() {
      final var time = System.currentTimeMillis();
      Event event;
      while ((event = events.peek()) != null) {
        if (event.deadlineMillis() <= time) {
          event.task().run();
          events.remove();
        } else {
          return;
        }
      }
    }

    public void start() {
      new Thread(this).start();
    }

    @Override
    public void run() {
      while (running) {
        try {
          runEvents();
          final var record = recordQueue.poll(500L, TimeUnit.MILLISECONDS);
          if (record != null) {
            exporter.export(record);
          }
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException(e);
        } catch (final Exception e) {
          LOGGER.warn("Unexpected exception occurred while running async exporter wrapper.", e);
        }
      }
    }

    @Override
    public void close() {
      running = false;
    }
  }
}
