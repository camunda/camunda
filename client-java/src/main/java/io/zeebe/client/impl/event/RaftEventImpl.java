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
package io.zeebe.client.impl.event;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.zeebe.client.api.events.*;
import io.zeebe.client.impl.data.ZeebeObjectMapperImpl;
import io.zeebe.client.impl.record.RecordImpl;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import java.util.List;

public class RaftEventImpl extends RecordImpl implements RaftEvent {
  private List<RaftMember> members;

  @JsonCreator
  public RaftEventImpl(@JacksonInject ZeebeObjectMapperImpl objectMapper) {
    super(objectMapper, RecordType.EVENT, ValueType.RAFT);
  }

  @Override
  @JsonDeserialize(contentAs = RaftMemberImpl.class)
  public List<RaftMember> getMembers() {
    return members;
  }

  public void setMembers(final List<RaftMember> members) {
    this.members = members;
  }

  @JsonIgnore
  @Override
  public RaftState getState() {
    return RaftState.valueOf(getMetadata().getIntent());
  }

  @Override
  public Class<? extends RecordImpl> getEventClass() {
    return RaftEventImpl.class;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("RaftEvent [state=");
    builder.append(getState());
    builder.append(", members=");
    builder.append(members);
    builder.append("]");
    return builder.toString();
  }
}
