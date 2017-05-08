package org.camunda.tngp.broker.workflow.graph.transformer;

import org.camunda.bpm.model.xml.instance.DomElement;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;

import static org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.*;

/**
 * Represents a builder to create the IO mapping fluently.
 */
public class IOMappingBuilder
{
    public static final String MAPPING_ATTRIBUTE_SOURCE = "source";
    public static final String MAPPING_ATTRIBUTE_TARGET = "target";

    private final TngpModelInstance tngpModelInstance;
    private final ModelElementInstance ioMapping;

    public IOMappingBuilder(TngpModelInstance tngpModelInstance, ModelElementInstance ioMapping)
    {
        this.ioMapping = ioMapping;
        this.tngpModelInstance = tngpModelInstance;
    }

    public IOMappingBuilder input(String source, String target)
    {
        addMappingElement(INPUT_MAPPING_ELEMENT, source, target);
        return this;
    }

    public IOMappingBuilder output(String source, String target)
    {
        addMappingElement(OUTPUT_MAPPING_ELEMENT, source, target);
        return this;
    }

    public TngpModelInstance done()
    {
        return tngpModelInstance;
    }

    private void addMappingElement(String mappingElement, String source, String target)
    {
        final DomElement inputMapping = tngpModelInstance.getDocument().createElement(TNGP_NAMESPACE, mappingElement);
        inputMapping.setAttribute(MAPPING_ATTRIBUTE_SOURCE, source);
        inputMapping.setAttribute(MAPPING_ATTRIBUTE_TARGET, target);
        ioMapping.getDomElement().appendChild(inputMapping);
    }
}
