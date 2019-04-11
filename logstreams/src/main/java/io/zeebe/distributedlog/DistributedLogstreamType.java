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
package io.zeebe.distributedlog;

import io.atomix.primitive.PrimitiveManagementService;
import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.service.PrimitiveService;
import io.atomix.primitive.service.ServiceConfig;
import io.atomix.utils.serializer.Namespace;
import io.atomix.utils.serializer.Namespaces;
import io.zeebe.distributedlog.impl.DefaultDistributedLogstreamBuilder;
import io.zeebe.distributedlog.impl.DefaultDistributedLogstreamService;
import io.zeebe.distributedlog.impl.DistributedLogstreamConfig;
import io.zeebe.distributedlog.impl.DistributedLogstreamServiceConfig;

public class DistributedLogstreamType
    implements PrimitiveType<
        DistributedLogstreamBuilder, DistributedLogstreamConfig, DistributedLogstream> {

  public static PrimitiveType instance() {

    return new DistributedLogstreamType();
  }

  @Override
  public String name() {
    return "Distributed-logstream";
  }

  @Override
  public Namespace namespace() {
    return Namespace.builder()
        .register(Namespaces.BASIC)
        .register(
            new Class[] {
              ServiceConfig.class, DistributedLogstreamServiceConfig.class, CommitLogEvent.class
            })
        .build();
  }

  @Override
  public DistributedLogstreamConfig newConfig() {
    return new DistributedLogstreamConfig();
  }

  @Override
  public DistributedLogstreamBuilder newBuilder(
      String s,
      DistributedLogstreamConfig distributedLogstreamConfig,
      PrimitiveManagementService primitiveManagementService) {
    return new DefaultDistributedLogstreamBuilder(
        s, distributedLogstreamConfig, primitiveManagementService);
  }

  @Override
  public PrimitiveService newService(ServiceConfig serviceConfig) {
    return new DefaultDistributedLogstreamService(
        (DistributedLogstreamServiceConfig) serviceConfig);
  }
}
