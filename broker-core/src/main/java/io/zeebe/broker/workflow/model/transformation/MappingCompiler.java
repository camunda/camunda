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
package io.zeebe.broker.workflow.model.transformation;

import io.zeebe.model.bpmn.instance.BaseElement;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeMappingType;
import io.zeebe.model.bpmn.instance.zeebe.ZeebePayloadMappings;
import io.zeebe.msgpack.mapping.Mapping;
import io.zeebe.msgpack.mapping.Mapping.Type;
import io.zeebe.msgpack.mapping.MappingBuilder;
import java.util.EnumMap;

public class MappingCompiler {

  private static final Mapping[] NO_MAPPINGS = new Mapping[0];
  private static final EnumMap<ZeebeMappingType, Mapping.Type> TYPE_MAP =
      new EnumMap<>(ZeebeMappingType.class);

  static {
    TYPE_MAP.put(ZeebeMappingType.PUT, Type.PUT);
    TYPE_MAP.put(ZeebeMappingType.COLLECT, Type.COLLECT);
  }

  private final MappingBuilder mappingBuilder = new MappingBuilder();

  public Mapping[] compilePayloadMappings(BaseElement element) {
    final ZeebePayloadMappings mappings =
        element.getSingleExtensionElement(ZeebePayloadMappings.class);

    if (mappings != null) {
      mappings
          .getMappings()
          .forEach(
              m -> mappingBuilder.mapping(m.getSource(), m.getTarget(), TYPE_MAP.get(m.getType())));

      return mappingBuilder.build();
    } else {
      return NO_MAPPINGS;
    }
  }
}
