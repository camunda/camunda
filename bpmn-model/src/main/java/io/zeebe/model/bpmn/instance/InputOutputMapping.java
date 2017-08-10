package io.zeebe.model.bpmn.instance;

import java.util.Map;

import io.zeebe.msgpack.mapping.Mapping;

public interface InputOutputMapping
{
    String DEFAULT_MAPPING = "$";

    Mapping[] getInputMappings();

    Mapping[] getOutputMappings();

    Map<String, String> getInputMappingsAsMap();

    Map<String, String> getOutputMappingsAsMap();

}
