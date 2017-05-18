package org.camunda.tngp.msgpack.mapping;

import org.camunda.tngp.msgpack.jsonpath.JsonPathQueryCompiler;

import java.util.ArrayList;
import java.util.List;

class MappingBuilder
{
    private List<Mapping> mappings = new ArrayList<>();

    protected static Mapping createMapping(String source, String target)
    {
        return createMappings().mapping(source, target).build()[0];
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
