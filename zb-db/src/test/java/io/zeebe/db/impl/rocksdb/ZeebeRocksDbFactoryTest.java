/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.db.impl.rocksdb;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.db.ZeebeDb;
import io.zeebe.db.ZeebeDbFactory;
import io.zeebe.db.impl.DefaultColumnFamily;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ZeebeRocksDbFactoryTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void shouldCreateNewDb() throws Exception {
    // given
    final ZeebeDbFactory<DefaultColumnFamily> dbFactory =
        ZeebeRocksDbFactory.newFactory(DefaultColumnFamily.class);

    final File pathName = temporaryFolder.newFolder();

    // when
    final ZeebeDb<DefaultColumnFamily> db = dbFactory.createDb(pathName);

    // then
    assertThat(pathName.listFiles()).isNotEmpty();
    db.close();
  }

  @Test
  public void shouldCreateTwoNewDbs() throws Exception {
    // given
    final ZeebeDbFactory<DefaultColumnFamily> dbFactory =
        ZeebeRocksDbFactory.newFactory(DefaultColumnFamily.class);
    final File firstPath = temporaryFolder.newFolder();
    final File secondPath = temporaryFolder.newFolder();

    // when
    final ZeebeDb<DefaultColumnFamily> firstDb = dbFactory.createDb(firstPath);
    final ZeebeDb<DefaultColumnFamily> secondDb = dbFactory.createDb(secondPath);

    // then
    assertThat(firstDb).isNotEqualTo(secondDb);

    assertThat(firstPath.listFiles()).isNotEmpty();
    assertThat(secondPath.listFiles()).isNotEmpty();

    firstDb.close();
    secondDb.close();
  }
}
