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
package io.zeebe.msgpack.mapping;

import io.zeebe.msgpack.jsonpath.JsonPathQuery;
import io.zeebe.msgpack.jsonpath.JsonPathQueryCompiler;
import java.util.ArrayList;
import java.util.List;

public class MappingBuilder {

  private final JsonPathQueryCompiler queryCompiler = new JsonPathQueryCompiler();

  private List<Mapping> mappings = new ArrayList<>();

  public static Mapping[] createMapping(String source, String target) {
    return createMappings().mapping(source, target).build();
  }

  public static Mapping[] createMapping(String source, String target, Mapping.Type type) {
    return createMappings().mapping(source, target, type).build();
  }

  protected static MappingBuilder createMappings() {
    return new MappingBuilder();
  }

  public MappingBuilder mapping(String source, String target) {
    return mapping(source, target, Mapping.Type.PUT);
  }

  public MappingBuilder mapping(String source, String target, Mapping.Type type) {
    final JsonPathQuery sourceQuery = queryCompiler.compile(source);

    // merging algorithm expect a root object $
    final String targetPath = "$." + target;
    final JsonPathPointer targetPointer = new JsonPathPointer(targetPath.split("\\."));

    mappings.add(new Mapping(sourceQuery, targetPointer, type));
    return this;
  }

  public Mapping[] build() {
    final Mapping[] result = mappings.toArray(new Mapping[mappings.size()]);
    mappings.clear();

    return result;
  }
}
