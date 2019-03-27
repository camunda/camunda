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
package io.zeebe.logstreams.impl.log.index;

import io.zeebe.db.DbContext;
import io.zeebe.db.impl.DbLong;

/**
 * The LogBlockIndexContext contains the required state to interact with LogBlockIndex concurrently.
 * This includes key and value instances, require to access the same column family in a thread-safe
 * manner, as well as a DbContext, required to access the database (also in a thread-safe manner).
 */
public class LogBlockIndexContext {
  private final DbContext dbContext;

  private final DbLong keyInstance = new DbLong();
  private final DbLong valueInstance = new DbLong();

  public LogBlockIndexContext(DbContext dbContext) {
    this.dbContext = dbContext;
  }

  public DbLong getKeyInstance() {
    return keyInstance;
  }

  public DbLong getValueInstance() {
    return valueInstance;
  }

  public DbLong writeKeyInstance(long key) {
    keyInstance.wrapLong(key);
    return keyInstance;
  }

  public DbLong writeValueInstance(long value) {
    valueInstance.wrapLong(value);
    return valueInstance;
  }

  public DbContext getDbContext() {
    return dbContext;
  }
}
