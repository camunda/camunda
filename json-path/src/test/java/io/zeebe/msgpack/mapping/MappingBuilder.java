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

import java.util.ArrayList;
import java.util.List;

import io.zeebe.msgpack.jsonpath.JsonPathQueryCompiler;

class MappingBuilder
{
    private List<Mapping> mappings = new ArrayList<>();

    protected static Mapping[] createMapping(String source, String target)
    {
        return createMappings().mapping(source, target).build();
    }

    protected static MappingBuilder createMappings()
    {
        return new MappingBuilder();
    }

    protected MappingBuilder mapping(String source, String target)
    {
        mappings.add(new Mapping(new JsonPathQueryCompiler().compile(source), target));
        return this;
    }

    protected Mapping[] build()
    {
        return mappings.toArray(new Mapping[mappings.size()]);
    }
}
