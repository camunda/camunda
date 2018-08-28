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
package io.zeebe.broker.exporter.record.value.raft;

import io.zeebe.exporter.record.value.raft.RaftMember;
import java.util.Objects;

public class RaftMemberImpl implements RaftMember {
  private final int nodeId;

  public RaftMemberImpl(final int nodeId) {
    this.nodeId = nodeId;
  }

  @Override
  public int getNodeId() {
    return nodeId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final RaftMemberImpl that = (RaftMemberImpl) o;
    return nodeId == that.nodeId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(nodeId);
  }

  @Override
  public String toString() {
    return "RaftMemberImpl{" + "nodeId=" + nodeId + '}';
  }
}
