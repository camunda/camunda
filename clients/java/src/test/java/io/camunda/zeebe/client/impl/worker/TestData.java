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
package io.camunda.zeebe.client.impl.worker;

import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class TestData {
  static ActivatedJob job() {
    return job(12);
  }

  static ActivatedJob job(final long key) {
    return ActivatedJob.newBuilder()
        .setKey(key)
        .setType("foo")
        .setProcessInstanceKey(123)
        .setBpmnProcessId("test1")
        .setProcessDefinitionVersion(2)
        .setProcessDefinitionKey(23)
        .setElementId("foo")
        .setElementInstanceKey(23213)
        .setCustomHeaders("{\"version\": \"1\"}")
        .setWorker("worker1")
        .setRetries(34)
        .setDeadline(1231)
        .setVariables("{\"key\": \"val\"}")
        .build();
  }

  static List<ActivatedJob> jobs(final int numberOfJobs) {
    return IntStream.range(0, numberOfJobs).mapToObj(TestData::job).collect(Collectors.toList());
  }
}
