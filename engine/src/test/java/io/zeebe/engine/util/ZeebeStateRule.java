/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.util;

import io.zeebe.db.ZeebeDb;
import io.zeebe.engine.processor.KeyGenerator;
import io.zeebe.engine.state.DefaultZeebeDbFactory;
import io.zeebe.engine.state.ZbColumnFamilies;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.protocol.Protocol;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;

public class ZeebeStateRule extends ExternalResource {

  private final TemporaryFolder tempFolder = new TemporaryFolder();
  private ZeebeDb<ZbColumnFamilies> db;
  private ZeebeState zeebeState;
  private final int partition;

  public ZeebeStateRule() {
    this(Protocol.DEPLOYMENT_PARTITION);
  }

  public ZeebeStateRule(int partition) {
    this.partition = partition;
  }

  @Override
  protected void before() throws Throwable {
    tempFolder.create();
    db = createNewDb();

    zeebeState = new ZeebeState(partition, db, db.createContext());
  }

  @Override
  protected void after() {
    try {
      db.close();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public ZeebeState getZeebeState() {
    return zeebeState;
  }

  public KeyGenerator getKeyGenerator() {
    return zeebeState.getKeyGenerator();
  }

  public ZeebeDb<ZbColumnFamilies> createNewDb() {
    try {
      final ZeebeDb<ZbColumnFamilies> db =
          DefaultZeebeDbFactory.DEFAULT_DB_FACTORY.createDb(tempFolder.newFolder());

      return db;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
