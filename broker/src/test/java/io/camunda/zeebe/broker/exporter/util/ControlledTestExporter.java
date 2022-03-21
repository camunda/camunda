/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.exporter.util;

import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.record.Record;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class ControlledTestExporter implements Exporter {
  private final List<Record<?>> exportedRecords = new CopyOnWriteArrayList<>();

  private boolean shouldAutoUpdatePosition;
  private Consumer<Context> onConfigure;
  private Consumer<Controller> onOpen;
  private Runnable onClose;
  private Consumer<Record<?>> onExport;

  private Context context;
  private Controller controller;

  public ControlledTestExporter shouldAutoUpdatePosition(final boolean shouldAutoUpdate) {
    shouldAutoUpdatePosition = shouldAutoUpdate;
    return this;
  }

  public ControlledTestExporter onConfigure(final Consumer<Context> callback) {
    onConfigure = callback;
    return this;
  }

  public ControlledTestExporter onOpen(final Consumer<Controller> callback) {
    onOpen = callback;
    return this;
  }

  public ControlledTestExporter onClose(final Runnable callback) {
    onClose = callback;
    return this;
  }

  public ControlledTestExporter onExport(final Consumer<Record<?>> callback) {
    onExport = callback;
    return this;
  }

  public Context getContext() {
    return context;
  }

  public void setContext(final Context context) {
    this.context = context;
  }

  public Controller getController() {
    return controller;
  }

  public void setController(final Controller controller) {
    this.controller = controller;
  }

  public List<Record<?>> getExportedRecords() {
    return exportedRecords;
  }

  @Override
  public void configure(final Context context) {
    this.context = context;

    if (onConfigure != null) {
      onConfigure.accept(context);
    }
  }

  @Override
  public void open(final Controller controller) {
    this.controller = controller;

    if (onOpen != null) {
      onOpen.accept(controller);
    }
  }

  @Override
  public void close() {
    if (onClose != null) {
      onClose.run();
    }
  }

  @Override
  public void export(final Record<?> record) {
    final Record<?> copiedRecord = record.copyOf();
    if (onExport != null) {
      onExport.accept(copiedRecord);
    }

    exportedRecords.add(copiedRecord);

    if (shouldAutoUpdatePosition) {
      getController().updateLastExportedRecordPosition(copiedRecord.getPosition());
    }
  }
}
