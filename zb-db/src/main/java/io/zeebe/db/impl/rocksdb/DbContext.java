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

import io.zeebe.db.TransactionOperation;
import io.zeebe.db.ZeebeDbException;
import io.zeebe.db.impl.rocksdb.transaction.ZeebeTransaction;
import io.zeebe.db.impl.rocksdb.transaction.ZeebeTransactionDb;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.rocksdb.RocksDBException;
import org.rocksdb.Status;
import org.rocksdb.Transaction;
import org.rocksdb.WriteOptions;

/**
 * Represents the execution context a DB instance. Contains the state needed by an actor to complete
 * operations in the DB, including buffers and the transaction object.
 */
public class DbContext {
  public static final String TRANSACTION_ERROR =
      "Unexpected error occurred during RocksDB transaction.";

  private final ExpandableArrayBuffer keyBuffer = new ExpandableArrayBuffer();
  private final ExpandableArrayBuffer valueBuffer = new ExpandableArrayBuffer();
  private final DirectBuffer keyViewBuffer = new UnsafeBuffer(0, 0);
  private final DirectBuffer valueViewBuffer = new UnsafeBuffer(0, 0);
  private final BufferSupplier bufferSupplier = new BufferSupplier();

  private Function<WriteOptions, Transaction> transactionProvider;
  private ZeebeTransaction currentZeebeTransaction;
  private WriteOptions writeOptions;

  public ExpandableArrayBuffer getKeyBuffer() {
    return keyBuffer;
  }

  public ExpandableArrayBuffer getValueBuffer() {
    return valueBuffer;
  }

  public DirectBuffer getKeyViewBuffer() {
    return keyViewBuffer;
  }

  public DirectBuffer getValueViewBuffer() {
    return valueViewBuffer;
  }

  public ZeebeTransaction getCurrentTransaction() {
    return currentZeebeTransaction;
  }

  public BufferSupplier getPrefixBufferSupplier() {
    return bufferSupplier;
  }

  public void setTransactionProvider(
      final Function<WriteOptions, Transaction> transactionDelegate) {
    this.transactionProvider = transactionDelegate;
  }

  /**
   * Runs the commands like delete, put etc. in a transaction. Access of different column families
   * inside this transaction are possible. If a previous transaction is already running, the
   * operations will be executed in the same transaction.
   *
   * <p>Reading key-value pairs via get or an iterator is also possible and will reflect changes,
   * which are made during the transaction.
   *
   * @param operations the operations
   * @throws ZeebeDbException is thrown on an unexpected error in the database layer
   * @throws RuntimeException is thrown on an unexpected error in executing the operations
   */
  public void runInTransaction(TransactionOperation operations) {
    try {
      if (currentZeebeTransaction != null) {
        operations.run();
      } else {
        runInNewTransaction(operations);
      }
    } catch (RocksDBException rdbex) {
      if (isRocksDbExceptionRecoverable(rdbex)) {
        throw new ZeebeDbException(TRANSACTION_ERROR, rdbex);
      } else {
        throw new RuntimeException(TRANSACTION_ERROR, rdbex);
      }
    } catch (Exception ex) {
      throw new RuntimeException(TRANSACTION_ERROR, ex);
    }
  }

  private boolean isRocksDbExceptionRecoverable(RocksDBException rdbex) {
    final Status status = rdbex.getStatus();
    return ZeebeTransactionDb.RECOVERABLE_ERROR_CODES.contains(status.getCode());
  }

  public void close() {
    if (writeOptions != null) {
      writeOptions.close();
      writeOptions = null;
    }
  }

  private void runInNewTransaction(final TransactionOperation operations) throws Exception {
    final ZeebeTransaction transaction = getTransaction(getWriteOptions());

    try {
      operations.run();
      transaction.commit();
    } catch (Exception e) {
      transaction.close();
      throw e;
    } finally {
      currentZeebeTransaction.close();
      currentZeebeTransaction = null;
    }
  }

  private WriteOptions getWriteOptions() {
    if (writeOptions == null) {
      writeOptions = new WriteOptions();
    }

    return writeOptions;
  }

  private ZeebeTransaction getTransaction(WriteOptions options) {
    if (currentZeebeTransaction == null) {
      currentZeebeTransaction = new ZeebeTransaction(transactionProvider.apply(options));
    }

    return currentZeebeTransaction;
  }

  public static class BufferSupplier implements AutoCloseable {
    private int activePrefixIterations = 0;
    private final ExpandableArrayBuffer[] prefixKeyBuffers =
        new ExpandableArrayBuffer[] {new ExpandableArrayBuffer(), new ExpandableArrayBuffer()};

    public ExpandableArrayBuffer getAvailablePrefixBuffer() {
      if (activePrefixIterations < prefixKeyBuffers.length) {
        return prefixKeyBuffers[activePrefixIterations++];
      }
      return null;
    }

    @Override
    public void close() {
      activePrefixIterations--;
    }
  }
}
