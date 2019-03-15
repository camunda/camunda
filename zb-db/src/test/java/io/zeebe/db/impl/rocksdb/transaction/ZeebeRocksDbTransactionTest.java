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
package io.zeebe.db.impl.rocksdb.transaction;

import io.zeebe.db.ZeebeDb;
import io.zeebe.db.ZeebeDbException;
import io.zeebe.db.ZeebeDbFactory;
import io.zeebe.db.impl.DefaultColumnFamily;
import io.zeebe.db.impl.DefaultZeebeDbFactory;
import java.io.File;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.rocksdb.RocksDBException;
import org.rocksdb.Status;
import org.rocksdb.Status.Code;
import org.rocksdb.Status.SubCode;

public class ZeebeRocksDbTransactionTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private final ZeebeDbFactory<DefaultColumnFamily> dbFactory =
      DefaultZeebeDbFactory.getDefaultFactory(DefaultColumnFamily.class);

  private ZeebeDb<DefaultColumnFamily> zeebeDb;

  @Before
  public void setup() throws Exception {
    final File pathName = temporaryFolder.newFolder();
    zeebeDb = dbFactory.createDb(pathName);
  }

  @Test(expected = ZeebeDbException.class)
  public void shouldThrowRecoverableException() {
    // given
    final Status status = new Status(Code.IOError, SubCode.None, "");

    // when
    zeebeDb.transaction(
        () -> {
          throw new RocksDBException("expected", status);
        });
  }

  @Test(expected = RuntimeException.class)
  public void shouldThrowNonRecoverableException() {
    // given
    final Status status = new Status(Code.NotSupported, SubCode.None, "");

    // when
    zeebeDb.transaction(
        () -> {
          throw new RocksDBException("expected", status);
        });
  }
}
