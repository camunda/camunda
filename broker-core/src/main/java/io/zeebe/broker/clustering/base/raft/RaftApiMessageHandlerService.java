/*
 * Zeebe Broker Core
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
package io.zeebe.broker.clustering.base.raft;

import io.zeebe.raft.Raft;
import io.zeebe.raft.RaftApiMessageHandler;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceGroupReference;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;

public class RaftApiMessageHandlerService implements Service<RaftApiMessageHandler> {
  protected RaftApiMessageHandler service;

  protected final ServiceGroupReference<Raft> raftGroupReference =
      ServiceGroupReference.<Raft>create()
          .onAdd((name, raft) -> service.registerRaft(raft))
          .onRemove((name, raft) -> service.removeRaft(raft))
          .build();

  @Override
  public void start(ServiceStartContext startContext) {
    service = new RaftApiMessageHandler();
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    // nothing to do
  }

  @Override
  public RaftApiMessageHandler get() {
    return service;
  }

  public ServiceGroupReference<Raft> getRaftGroupReference() {
    return raftGroupReference;
  }
}
