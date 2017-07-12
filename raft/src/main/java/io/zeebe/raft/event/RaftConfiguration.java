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
package io.zeebe.raft.event;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.ArrayProperty;
import io.zeebe.msgpack.spec.MsgPackHelper;
import io.zeebe.msgpack.value.ArrayValue;
import io.zeebe.msgpack.value.ArrayValueIterator;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class RaftConfiguration extends UnpackedObject
{
    protected static final DirectBuffer EMPTY_ARRAY = new UnsafeBuffer(MsgPackHelper.EMPTY_ARRAY);

    protected RaftConfigurationMember raftConfigurationMember = new RaftConfigurationMember();
    protected ArrayProperty<RaftConfigurationMember> membersProp = new ArrayProperty<>("members", new ArrayValue<>(),
        new ArrayValue<>(EMPTY_ARRAY, 0, EMPTY_ARRAY.capacity()), raftConfigurationMember);

    public RaftConfiguration()
    {
        declareProperty(membersProp);
    }

    public ArrayValueIterator<RaftConfigurationMember> members()
    {
        return membersProp;
    }

}
