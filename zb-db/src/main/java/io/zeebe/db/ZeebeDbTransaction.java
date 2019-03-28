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
package io.zeebe.db;

/** Represents an Zeebe DB transaction, which can be committed or on error it can be rolled back. */
public interface ZeebeDbTransaction {

  /**
   * Runs the commands like delete, put etc. in the current transaction. Access of different column
   * families inside this transaction are possible.
   *
   * <p>Reading key-value pairs via get or an iterator is also possible and will reflect changes,
   * which are made during the transaction.
   *
   * @param operations the operations
   * @throws ZeebeDbException is thrown on an unexpected error in the database layer
   * @throws RuntimeException is thrown on an unexpected error in executing the operations
   */
  void run(TransactionOperation operations) throws Exception;

  /**
   * Commits the transaction and writes the data into the database.
   *
   * @throws ZeebeDbException if the underlying database has a recoverable exception thrown
   * @throws Exception if the underlying database has a non recoverable exception thrown
   */
  void commit() throws Exception;

  /**
   * Rolls the transaction back to the latest commit, discards all changes in between.
   *
   * @throws ZeebeDbException if the underlying database has a recoverable exception thrown
   * @throws Exception if the underlying database has a non recoverable exception thrown
   */
  void rollback() throws Exception;
}
