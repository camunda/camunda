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
package io.camunda.zeebe.protocol.record;

import org.immutables.value.Value;

/**
 * Identifies the inbound channel through which a command was received (e.g. {@code "MCP"}).
 *
 * <p>This is cross-cutting metadata: it is carried alongside records in the audit log so that
 * operators can distinguish MCP-triggered process-instance starts from REST/gRPC-triggered ones
 * without inspecting BPMN semantics.
 *
 * <p>Not annotated with {@link ImmutableProtocol} intentionally: that annotation causes {@code
 * ProtocolFactory} to scan for this type and register an EasyRandom randomizer, which leads to
 * concurrent test-initialisation failures. {@code RequestSource} is metadata on {@link Record} (a
 * default method returning {@code null}), not a first-class record value, so random generation is
 * not required.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableRequestSource.Builder.class)
public interface RequestSource {

  /** The type of the inbound channel, e.g. {@code "MCP"}. */
  String getChannelType();

  /**
   * The name of the tool that was called, when the channel supports named tools (e.g. MCP). May be
   * empty for channels that do not have a tool concept.
   */
  String getToolName();
}
