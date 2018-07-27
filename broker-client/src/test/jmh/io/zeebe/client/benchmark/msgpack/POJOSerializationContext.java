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

import io.zeebe.client.benchmark.msgpack.MsgPackSerializer.Type;
import io.zeebe.client.benchmark.msgpack.POJOFactory.InstantiateAlwaysFactory;
import io.zeebe.client.benchmark.msgpack.POJOFactory.ReuseObjectAndPropertiesFactory;
import io.zeebe.client.benchmark.msgpack.POJOFactory.ReuseObjectFactory;
import java.util.HashMap;
import java.util.Map;
import org.agrona.concurrent.UnsafeBuffer;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class POJOSerializationContext {
  @Param(
      value = {
        "REUSE_POJO_REUSE_PROPERTIES",
        "REUSE_POJO_INSTANTIATE_PROPERTIES",
        "INSTANTIATE_POJO_INSTANTIATE_PROPERTIES",
      })
  protected POJOFactory.Type pojoFactoryType;

  @Param(value = {"JACKSON", "BROKER"})
  protected MsgPackSerializer.Type serializerType;

  protected MsgPackSerializer serializer;
  protected POJOFactory pojoFactory;

  protected UnsafeBuffer targetBuffer = new UnsafeBuffer(new byte[1024 * 2]);

  protected Map<POJOFactory.Type, POJOFactory> jacksonPojoFactories = new HashMap<>();
  protected Map<POJOFactory.Type, POJOFactory> brokerPojoFactories = new HashMap<>();

  public POJOSerializationContext() {
    add(brokerPojoFactories, new ReuseObjectAndPropertiesFactory(new BrokerTaskEvent()));
    add(brokerPojoFactories, new ReuseObjectFactory(new BrokerTaskEvent()));
    add(brokerPojoFactories, new InstantiateAlwaysFactory(() -> new BrokerTaskEvent()));
    add(jacksonPojoFactories, new ReuseObjectAndPropertiesFactory(new JacksonTaskEvent()));
    add(jacksonPojoFactories, new ReuseObjectFactory(new JacksonTaskEvent()));
    add(jacksonPojoFactories, new InstantiateAlwaysFactory(() -> new JacksonTaskEvent()));
  }

  protected void add(Map<POJOFactory.Type, POJOFactory> factories, POJOFactory factory) {
    factories.put(factory.getType(), factory);
  }

  @Setup
  public void setUp() {
    if (serializerType == Type.BROKER) {
      serializer = new MsgPackBrokerSerializer();
      pojoFactory = brokerPojoFactories.get(pojoFactoryType);

    } else {
      serializer = new MsgPackJacksonSerializer();
      pojoFactory = jacksonPojoFactories.get(pojoFactoryType);
    }
  }

  public MsgPackSerializer getSerializer() {
    return serializer;
  }

  public POJOFactory getPojoFactory() {
    return pojoFactory;
  }

  public UnsafeBuffer getTargetBuffer() {
    return targetBuffer;
  }
}
