/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.exporter.stream;

import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.exporter.repo.ExporterLoadException;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.engine.state.ZbColumnFamilies;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.jar.ExternalJarLoadException;
import io.camunda.zeebe.util.sched.Actor;
import io.camunda.zeebe.util.sched.ActorControl;
import io.camunda.zeebe.util.sched.ActorScheduler;
import java.io.File;
import java.nio.file.Path;
import org.agrona.CloseHelper;

/**
 * A small utility class which provides all the required runtime components to unit test exporter
 * container.
 */
public final class ExporterContainerRuntime implements CloseableSilently {
  private final ActorScheduler scheduler;
  private final ExporterRepository repository;
  private final ZeebeDb<ZbColumnFamilies> zeebeDb;
  private final RuntimeActor actor;
  private final ExportersState state;
  private final ExporterMetrics metrics;

  public ExporterContainerRuntime(final Path storagePath) {
    scheduler = ActorScheduler.newActorScheduler().build();
    scheduler.start();

    repository = new ExporterRepository();
    zeebeDb = createZeebeDb(storagePath.resolve("db"));

    actor = new RuntimeActor();
    scheduler.submitActor(actor).join();

    state = new ExportersState(zeebeDb, zeebeDb.createContext());
    metrics = new ExporterMetrics(1);
  }

  @Override
  public void close() {
    CloseHelper.quietCloseAll(actor, scheduler, zeebeDb);
  }

  public ExporterDescriptor loadExternalExporter(final File jarFile, final String className)
      throws ExporterLoadException, ExternalJarLoadException {
    final var exporterCfg = new ExporterCfg();
    exporterCfg.setJarPath(jarFile.getAbsolutePath());
    exporterCfg.setClassName(className);

    return repository.load("external", exporterCfg);
  }

  public ExporterContainer newContainer(final ExporterDescriptor descriptor) {
    final var container = new ExporterContainer(descriptor);
    container.initContainer(actor.getActorControl(), metrics, state);

    return container;
  }

  public ExporterRepository getRepository() {
    return repository;
  }

  public ExportersState getState() {
    return state;
  }

  public RuntimeActor getActor() {
    return actor;
  }

  private ZeebeDb<ZbColumnFamilies> createZeebeDb(final Path path) {
    return DefaultZeebeDbFactory.defaultFactory().createDb(path.toFile());
  }

  static final class RuntimeActor extends Actor {
    ActorControl getActorControl() {
      return actor;
    }
  }
}
