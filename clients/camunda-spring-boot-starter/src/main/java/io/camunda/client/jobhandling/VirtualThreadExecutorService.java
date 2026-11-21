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
package io.camunda.client.jobhandling;

/**
 * Capability of a {@link CamundaClientExecutorService} whose job-handling executor runs on virtual
 * threads.
 *
 * <p>This is an explicit, type-level signal. The multi-client factory ({@code
 * CamundaClientFactory}) gives each named client its own executor and must know whether the shared
 * bean is virtual-threaded to decide what to build. It cannot sniff the executor's class name — the
 * JDK's {@code ThreadPerTaskExecutor} backs both virtual and platform thread-per-task factories,
 * and {@code ExecutorService#isVirtualThreadPerTaskExecutor()} only exists on Java 24+, while this
 * starter still compiles and runs on Java 17. An executor bean declares "my job handling runs on
 * virtual threads" authoritatively by implementing this interface.
 *
 * <p>The interface also supplies the per-client replacement: {@link #createOwnedExecutor()} builds
 * a fresh, independently-owned virtual-thread executor for one named client. The implementation
 * ({@code VirtualThreadCamundaClientExecutorService}) lives in the virtual-threads module ({@code
 * camunda-spring-boot-starter-virtual-threads}), the only module that builds virtual-thread
 * executors — it compiles at Java 21, while this starter compiles at Java 17 and so cannot
 * reference the virtual-thread API. That module depends on this starter, so the capability lives
 * here where the factory can reference it; the factory calls {@link #createOwnedExecutor()} without
 * itself touching any virtual-thread API.
 */
public interface VirtualThreadExecutorService {

  /**
   * Builds a new virtual-thread executor owned by a single named client — a virtual-thread
   * job-handling executor plus a single platform-thread scheduler, both owned by that client and
   * closed with it. Each named client gets its own, so one client's shutdown cannot stop job
   * handling for the others.
   */
  CamundaClientExecutorService createOwnedExecutor();
}
