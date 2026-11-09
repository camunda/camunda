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
package io.camunda.client.impl.util;

import io.camunda.client.api.command.VariableMetadataInput;
import io.camunda.client.api.search.enums.ProtectionMode;
import io.camunda.client.protocol.rest.ProtectionModeEnum;
import io.camunda.client.protocol.rest.VariableMetadata;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Maps variables metadata to the requests. */
public final class VariableMetadataMapper {

  private VariableMetadataMapper() {}

  /** Returns {@code null} when there is nothing to send. */
  public static Map<String, VariableMetadata> toRest(
      final Map<String, VariableMetadataInput> variablesMetadata) {
    final Map<String, VariableMetadata> result = new LinkedHashMap<>();
    variablesMetadata.forEach(
        (name, variableMetadata) ->
            result.put(
                name,
                new VariableMetadata()
                    .protectionModes(
                        variableMetadata.getProtectionModes().stream()
                            .map(mode -> EnumUtil.convert(mode, ProtectionModeEnum.class))
                            .collect(Collectors.toList()))));
    return result.isEmpty() ? null : result;
  }

  public static Map<String, GatewayOuterClass.VariableMetadata> toGrpc(
      final Map<String, VariableMetadataInput> variablesMetadata) {
    final Map<String, GatewayOuterClass.VariableMetadata> result = new LinkedHashMap<>();
    variablesMetadata.forEach(
        (name, variableMetadata) -> {
          final GatewayOuterClass.VariableMetadata.Builder builder =
              GatewayOuterClass.VariableMetadata.newBuilder();
          for (final ProtectionMode mode : variableMetadata.getProtectionModes()) {
            builder.addProtectionModes(
                EnumUtil.convert(mode, GatewayOuterClass.VariableMetadata.ProtectionMode.class));
          }
          result.put(name, builder.build());
        });
    return result;
  }
}
