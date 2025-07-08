/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.engine.state.ProcessingDbState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.stream.impl.state.DbKeyGenerator;
import io.camunda.zeebe.util.FeatureFlags;
import java.time.InstantSource;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;

public final class ProcessingStateRule extends ExternalResource {

  private final TemporaryFolder tempFolder = new TemporaryFolder();
  private final int partition;
  private ZeebeDb<ZbColumnFamilies> db;
  private MutableProcessingState processingState;

  public ProcessingStateRule() {
    this(Protocol.DEPLOYMENT_PARTITION);
  }

  public ProcessingStateRule(final int partition) {
    this.partition = partition;
  }

  @Override
  protected void before() throws Throwable {
    tempFolder.create();
    db = createNewDb();

    final var context = db.createContext();
    final var keyGenerator = new DbKeyGenerator(partition, db, context);
    processingState =
        new ProcessingDbState(
            partition,
            db,
            context,
            keyGenerator,
            new TransientPendingSubscriptionState(),
            new TransientPendingSubscriptionState(),
            new EngineConfiguration(),
            FeatureFlags.createDefaultForTests(),
            InstantSource.system());
  }

  @Override
  protected void after() {
    try {
      db.close();
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
    tempFolder.delete();
  }

  public MutableProcessingState getProcessingState() {
    return processingState;
  }

  public ZeebeDb<ZbColumnFamilies> createNewDb() {
    try {

      return DefaultZeebeDbFactory.defaultFactory().createDb(tempFolder.newFolder());
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }
}
