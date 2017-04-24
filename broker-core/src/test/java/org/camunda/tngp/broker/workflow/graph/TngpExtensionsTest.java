package org.camunda.tngp.broker.workflow.graph;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.*;

public class TngpExtensionsTest
{
    @Test
    public void shouldAddIOMappingWithMap()
    {
        // given
        final Map<String, String> inputMapping = new HashMap<>();
        inputMapping.put("inSource", "inTarget");

        final Map<String, String> outputMapping = new HashMap<>();
        outputMapping.put("outSource", "outTarget");

        // when
        final BpmnModelInstance bpmnModelInstance = wrap(Bpmn.createExecutableProcess()
            .startEvent("foo")
            .name("bar")
            .done())
            .ioMapping("foo", inputMapping, outputMapping);

        // then
        assertThatModelInstanceContainsIOMapping(bpmnModelInstance);
    }

    @Test
    public void shouldAddIOMappingWithBuilder()
    {
        // given

        // when
        final BpmnModelInstance bpmnModelInstance = wrap(Bpmn.createExecutableProcess()
            .startEvent("foo")
            .name("bar")
            .done())
            .ioMapping("foo")
                .input("inSource", "inTarget")
                .output("outSource", "outTarget")
            .done();

        // then
        assertThatModelInstanceContainsIOMapping(bpmnModelInstance);
    }

    private void assertThatModelInstanceContainsIOMapping(BpmnModelInstance bpmnModelInstance)
    {
        final ExtensionElements extensionElements = ((BaseElement) bpmnModelInstance.getModelElementById("foo")).getExtensionElements();
        assertThat(extensionElements).isNotNull();

        final ModelElementInstance ioMappingElement = extensionElements.getUniqueChildElementByNameNs(TNGP_NAMESPACE, IO_MAPPING_ELEMENT);
        assertThat(ioMappingElement).isNotNull();

        final List<DomElement> inputMappings = ioMappingElement.getDomElement().getChildElementsByNameNs(TNGP_NAMESPACE, INPUT_MAPPING_ELEMENT);
        assertThat(inputMappings).isNotNull();
        assertThat(inputMappings).isNotEmpty();
        assertThat(inputMappings.get(0).getAttribute(MAPPING_ATTRIBUTE_SOURCE)).isEqualTo("inSource");
        assertThat(inputMappings.get(0).getAttribute(MAPPING_ATTRIBUTE_TARGET)).isEqualTo("inTarget");

        final List<DomElement> outputMappings = ioMappingElement.getDomElement().getChildElementsByNameNs(TNGP_NAMESPACE, OUTPUT_MAPPING_ELEMENT);
        assertThat(outputMappings).isNotNull();
        assertThat(outputMappings).isNotEmpty();
        assertThat(outputMappings.get(0).getAttribute(MAPPING_ATTRIBUTE_SOURCE)).isEqualTo("outSource");
        assertThat(outputMappings.get(0).getAttribute(MAPPING_ATTRIBUTE_TARGET)).isEqualTo("outTarget");
    }


}
