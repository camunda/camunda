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

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import io.zeebe.db.DbContext;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.ZeebeDbException;
import io.zeebe.db.ZeebeDbFactory;
import io.zeebe.db.ZeebeDbTransaction;
import io.zeebe.db.impl.DefaultColumnFamily;
import io.zeebe.db.impl.DefaultZeebeDbFactory;
import io.zeebe.util.exception.RecoverableException;
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

  private DbContext dbContext;

  @Before
  public void setup() throws Exception {
    final File pathName = temporaryFolder.newFolder();
    final ZeebeDb<DefaultColumnFamily> zeebeDb = dbFactory.createDb(pathName);
    dbContext = zeebeDb.createContext();
  }

  @Test(expected = ZeebeDbException.class)
  public void shouldThrowRecoverableException() {
    // given
    final Status status = new Status(Code.IOError, SubCode.None, "");

    // when
    dbContext.runInTransaction(
        () -> {
          throw new RocksDBException("expected", status);
        });
  }

  @Test(expected = RecoverableException.class)
  public void shouldReThrowRecoverableException() {
    // given
    final Status status = new Status(Code.IOError, SubCode.None, "");

    // when
    dbContext.runInTransaction(
        () -> {
          throw new RecoverableException(new RocksDBException("expected", status));
        });
  }

  @Test(expected = RuntimeException.class)
  public void shouldWrapExceptionInRuntimeException() {
    // given
    final Status status = new Status(Code.NotSupported, SubCode.None, "");

    // when
    dbContext.runInTransaction(
        () -> {
          throw new RocksDBException("expected", status);
        });
  }

  @Test(expected = ZeebeDbException.class)
  public void shouldThrowRecoverableExceptionOnCommit() throws Exception {
    // given
    final ZeebeTransaction transaction = mock(ZeebeTransaction.class);
    final DbContext newContext = new DefaultDbContext(transaction);
    final Status status = new Status(Code.IOError, SubCode.None, "");
    doThrow(new RocksDBException("expected", status)).when(transaction).commitInternal();

    // when
    newContext.runInTransaction(() -> {});
  }

  @Test(expected = RuntimeException.class)
  public void shouldWrapExceptionInRuntimeExceptionOnCommit() throws Exception {
    // given
    final ZeebeTransaction transaction = mock(ZeebeTransaction.class);
    final DbContext newContext = new DefaultDbContext(transaction);
    final Status status = new Status(Code.NotSupported, SubCode.None, "");
    doThrow(new RocksDBException("expected", status)).when(transaction).commitInternal();

    // when
    newContext.runInTransaction(() -> {});
  }

  @Test(expected = ZeebeDbException.class)
  public void shouldThrowRecoverableExceptionOnRollback() throws Exception {
    // given
    final ZeebeTransaction transaction = mock(ZeebeTransaction.class);
    final DbContext newContext = new DefaultDbContext(transaction);
    final Status status = new Status(Code.IOError, SubCode.None, "");
    doThrow(new RocksDBException("expected", status)).when(transaction).rollbackInternal();

    // when
    newContext.runInTransaction(() -> {});
  }

  @Test(expected = RuntimeException.class)
  public void shouldWrapExceptionInRuntimeExceptionOnRollback() throws Exception {
    // given
    final ZeebeTransaction transaction = mock(ZeebeTransaction.class);
    final DbContext newContext = new DefaultDbContext(transaction);
    final Status status = new Status(Code.NotSupported, SubCode.None, "");
    doThrow(new RocksDBException("expected", status)).when(transaction).rollbackInternal();

    // when
    newContext.runInTransaction(() -> {});
  }

  @Test(expected = ZeebeDbException.class)
  public void shouldThrowRecoverableExceptionInTransactionRun() throws Exception {
    // given
    final Status status = new Status(Code.IOError, SubCode.None, "");

    // when
    final ZeebeDbTransaction currentTransaction = dbContext.getCurrentTransaction();
    currentTransaction.run(
        () -> {
          throw new RocksDBException("expected", status);
        });
  }

  @Test(expected = RecoverableException.class)
  public void shouldReThrowRecoverableExceptionInTransactionRun() throws Exception {
    // given
    final Status status = new Status(Code.IOError, SubCode.None, "");

    // when
    final ZeebeDbTransaction currentTransaction = dbContext.getCurrentTransaction();
    currentTransaction.run(
        () -> {
          throw new RecoverableException(new RocksDBException("expected", status));
        });
  }

  @Test(expected = RocksDBException.class)
  public void shouldReThrowExceptionFromTransactionRun() throws Exception {
    // given
    final Status status = new Status(Code.NotSupported, SubCode.None, "");

    // when
    final ZeebeDbTransaction currentTransaction = dbContext.getCurrentTransaction();
    currentTransaction.run(
        () -> {
          throw new RocksDBException("expected", status);
        });
  }

  @Test(expected = ZeebeDbException.class)
  public void shouldThrowRecoverableExceptionInTransactionCommit() throws Exception {
    // given
    final Status status = new Status(Code.IOError, SubCode.None, "");
    final ZeebeTransaction currentTransaction =
        spy((ZeebeTransaction) dbContext.getCurrentTransaction());
    doThrow(new RocksDBException("expected", status)).when(currentTransaction).commitInternal();

    // when
    currentTransaction.commit();
  }

  @Test(expected = RocksDBException.class)
  public void shouldReThrowExceptionFromTransactionCommit() throws Exception {
    // given
    final Status status = new Status(Code.NotSupported, SubCode.None, "");
    final ZeebeTransaction currentTransaction =
        spy((ZeebeTransaction) dbContext.getCurrentTransaction());
    doThrow(new RocksDBException("expected", status)).when(currentTransaction).commitInternal();

    // when
    currentTransaction.commit();
  }

  @Test(expected = ZeebeDbException.class)
  public void shouldThrowRecoverableExceptionInTransactionRollback() throws Exception {
    // given
    final Status status = new Status(Code.IOError, SubCode.None, "");
    final ZeebeTransaction currentTransaction =
        spy((ZeebeTransaction) dbContext.getCurrentTransaction());
    doThrow(new RocksDBException("expected", status)).when(currentTransaction).rollbackInternal();

    // when
    currentTransaction.rollback();
  }

  @Test(expected = RocksDBException.class)
  public void shouldReThrowExceptionFromTransactionRollback() throws Exception {
    // given
    final Status status = new Status(Code.NotSupported, SubCode.None, "");
    final ZeebeTransaction currentTransaction =
        spy((ZeebeTransaction) dbContext.getCurrentTransaction());
    doThrow(new RocksDBException("expected", status)).when(currentTransaction).rollbackInternal();

    // when
    currentTransaction.rollback();
  }
}
