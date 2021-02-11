/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.util;

import io.zeebe.db.ZeebeDb;
import io.zeebe.engine.state.DefaultZeebeDbFactory;
import io.zeebe.engine.state.KeyGenerator;
import io.zeebe.engine.state.ZbColumnFamilies;
import io.zeebe.engine.state.ZeebeDbState;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.protocol.Protocol;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;

public final class ZeebeStateRule extends ExternalResource {

  private final TemporaryFolder tempFolder = new TemporaryFolder();
  private final int partition;
  private ZeebeDb<ZbColumnFamilies> db;
  private ZeebeState zeebeState;

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

  public ZeebeState getZeebeState() {
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
