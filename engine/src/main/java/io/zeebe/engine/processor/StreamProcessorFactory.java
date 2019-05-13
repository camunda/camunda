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
package io.zeebe.engine.processor;

import io.zeebe.db.DbContext;
import io.zeebe.db.ZeebeDb;
import io.zeebe.util.sched.ActorControl;

@FunctionalInterface
public interface StreamProcessorFactory {

  /**
   * Creates an stream processor with the given attributes.
   *
   * @param actor the actor which is used for execution
   * @param zeebeDb the database to store the state of the processor
   * @param dbContext the context on which the processor should run
   * @return the created stream processor
   */
  StreamProcessor createProcessor(ActorControl actor, ZeebeDb zeebeDb, DbContext dbContext);
}
