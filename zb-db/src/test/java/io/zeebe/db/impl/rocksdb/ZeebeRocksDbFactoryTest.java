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
