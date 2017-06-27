package io.zeebe.broker.workflow.graph.transformer;

import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.INPUT_MAPPING_ELEMENT;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.OUTPUT_MAPPING_ELEMENT;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.ZEEBE_NAMESPACE;

import org.camunda.bpm.model.xml.instance.DomElement;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;

import io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.ZeebeModelInstance;

/**
 * Represents a builder to create the IO mapping fluently.
 */
public class IOMappingBuilder
{
    public static final String MAPPING_ATTRIBUTE_SOURCE = "source";
    public static final String MAPPING_ATTRIBUTE_TARGET = "target";

    private final ZeebeModelInstance zeebeModelInstance;
    private final ModelElementInstance ioMapping;

    public IOMappingBuilder(ZeebeModelInstance zeebeModelInstance, ModelElementInstance ioMapping)
    {
        this.ioMapping = ioMapping;
        this.zeebeModelInstance = zeebeModelInstance;
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

    public ZeebeModelInstance done()
    {
        return zeebeModelInstance;
    }

    private void addMappingElement(String mappingElement, String source, String target)
    {
        final DomElement inputMapping = zeebeModelInstance.getDocument().createElement(ZEEBE_NAMESPACE, mappingElement);
        inputMapping.setAttribute(MAPPING_ATTRIBUTE_SOURCE, source);
        inputMapping.setAttribute(MAPPING_ATTRIBUTE_TARGET, target);
        ioMapping.getDomElement().appendChild(inputMapping);
    }
}
