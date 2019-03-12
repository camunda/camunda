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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.DBOptions;
import org.rocksdb.OptimisticTransactionDB;
import org.rocksdb.RocksDBException;

class RocksDbRegistry {

  private static final ConcurrentHashMap<String, DbReference> DB_REGISTRY =
      new ConcurrentHashMap<>();

  static DbReference open(
      final DBOptions dbOptions,
      final String path,
      final List<ColumnFamilyDescriptor> columnFamilyDescriptors,
      final List<ColumnFamilyHandle> columnFamilyHandles) {
    final DbReference dbReference =
        DB_REGISTRY
            .computeIfAbsent(
                path,
                (missingPath) ->
                    createNewDbReference(dbOptions, missingPath, columnFamilyDescriptors))
            .newReference();

    columnFamilyHandles.addAll(dbReference.getFamilyHandles());
    return dbReference;
  }

  private static DbReference createNewDbReference(
      final DBOptions dbOptions,
      final String missingPath,
      final List<ColumnFamilyDescriptor> columnFamilyDescriptors) {
    try {
      final List<ColumnFamilyHandle> familyHandles = new ArrayList<>();

      final OptimisticTransactionDB transactionDB =
          OptimisticTransactionDB.open(
              dbOptions, missingPath, columnFamilyDescriptors, familyHandles);
      return new DbReference(missingPath, transactionDB, familyHandles, DB_REGISTRY::remove);
    } catch (final RocksDBException e) {
      throw new RuntimeException("Unexpected error occurred trying to open the database", e);
    }
  }
}
