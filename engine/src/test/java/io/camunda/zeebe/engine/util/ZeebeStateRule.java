/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.util;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.engine.state.KeyGenerator;
import io.camunda.zeebe.engine.state.ZbColumnFamilies;
import io.camunda.zeebe.engine.state.ZeebeDbState;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.protocol.Protocol;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;

public final class ZeebeStateRule extends ExternalResource {

  private final TemporaryFolder tempFolder = new TemporaryFolder();
  private final int partition;
  private ZeebeDb<ZbColumnFamilies> db;
  private MutableZeebeState zeebeState;

  public ZeebeStateRule() {
    this(Protocol.DEPLOYMENT_PARTITION);
  }

  public ZeebeStateRule(final int partition) {
    this.partition = partition;
  }

  @Override
  protected void before() throws Throwable {
    tempFolder.create();
    db = createNewDb();

    zeebeState = new ZeebeDbState(partition, db, db.createContext());
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

  public MutableZeebeState getZeebeState() {
    return zeebeState;
  }

  public KeyGenerator getKeyGenerator() {
    return zeebeState.getKeyGenerator();
  }

  public ZeebeDb<ZbColumnFamilies> createNewDb() {
    try {

      return DefaultZeebeDbFactory.defaultFactory().createDb(tempFolder.newFolder());
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }
}
