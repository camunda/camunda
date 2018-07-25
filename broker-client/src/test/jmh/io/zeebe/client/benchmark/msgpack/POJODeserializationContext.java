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
package io.zeebe.client.benchmark.msgpack;

import static io.zeebe.util.StringUtil.getBytes;

import io.zeebe.client.benchmark.msgpack.MsgPackSerializer.Type;
import io.zeebe.msgpack.spec.MsgPackWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class POJODeserializationContext {

  protected static final DirectBuffer PAYLOAD = new UnsafeBuffer(new byte[1024 * 1]);

  protected MutableDirectBuffer msgpackBuf = new UnsafeBuffer(new byte[1024 * 105]);

  @Param(value = {"JACKSON", "BROKER"})
  protected MsgPackSerializer.Type serializerType;

  protected MsgPackSerializer serializer;
  protected Class<?> targetClass;

  public POJODeserializationContext() {
    initMsgPack();
  }

  protected void initMsgPack() {
    final MsgPackWriter writer = new MsgPackWriter();
    writer.wrap(msgpackBuf, 0);
    writer.writeMapHeader(5);

    writer.writeString(utf8("eventType"));
    writer.writeString(utf8(TaskEventType.ABORT_FAILED.toString()));

    writer.writeString(utf8("lockTime"));
    writer.writeInteger(123123123L);

    writer.writeString(utf8("type"));
    writer.writeString(utf8("foofoobarbaz"));

    writer.writeString(utf8("headers"));
    writer.writeMapHeader(3);
    writer.writeString(utf8("key1"));
    writer.writeString(utf8("val1"));
    writer.writeString(utf8("key2"));
    writer.writeString(utf8("val2"));
    writer.writeString(utf8("key3"));
    writer.writeString(utf8("val3"));

    writer.writeString(utf8("payload"));
    writer.writeBinary(PAYLOAD);

    msgpackBuf.wrap(msgpackBuf, 0, writer.getOffset());
  }

  @Setup
  public void setUp() {
    if (serializerType == Type.BROKER) {
      serializer = new MsgPackBrokerSerializer();
      targetClass = BrokerTaskEvent.class;
    } else {
      serializer = new MsgPackJacksonSerializer();
      targetClass = JacksonTaskEvent.class;
    }
  }

  public MsgPackSerializer getSerializer() {
    return serializer;
  }

  protected static DirectBuffer utf8(String value) {
    return new UnsafeBuffer(getBytes(value));
  }

  public DirectBuffer getMsgpackBuffer() {
    return msgpackBuf;
  }

  public Class<?> getTargetClass() {
    return targetClass;
  }
}
