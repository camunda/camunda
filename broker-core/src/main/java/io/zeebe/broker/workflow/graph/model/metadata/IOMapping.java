package io.zeebe.broker.workflow.graph.model.metadata;

import io.zeebe.msgpack.mapping.Mapping;
/**
 * Represents an IO mapping structure. The IO mapping can consist
 * of a list of different input and output mapping's for a flow element.
 * Each input and output mapping has a source and target. The source and target
 * are represented via a json path expression.
 */
public class IOMapping
{
    private Mapping inputMappings[];
    private Mapping outputMappings[];

    public Mapping[] getInputMappings()
    {
        return this.inputMappings;
    }

    public void setInputMappings(Mapping[] inputMappings)
    {
        this.inputMappings = inputMappings;
    }

    public Mapping[] getOutputMappings()
    {
        return this.outputMappings;
    }

    public void setOutputMappings(Mapping[] outputMappings)
    {
        this.outputMappings = outputMappings;
    }
}
