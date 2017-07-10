/**
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
package io.zeebe.broker.workflow.graph;

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
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.*;

public class ZeebeExtensionsTest
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

        final ModelElementInstance ioMappingElement = extensionElements.getUniqueChildElementByNameNs(ZEEBE_NAMESPACE, IO_MAPPING_ELEMENT);
        assertThat(ioMappingElement).isNotNull();

        final List<DomElement> inputMappings = ioMappingElement.getDomElement().getChildElementsByNameNs(ZEEBE_NAMESPACE, INPUT_MAPPING_ELEMENT);
        assertThat(inputMappings).isNotNull();
        assertThat(inputMappings).isNotEmpty();
        assertThat(inputMappings.get(0).getAttribute(MAPPING_ATTRIBUTE_SOURCE)).isEqualTo("inSource");
        assertThat(inputMappings.get(0).getAttribute(MAPPING_ATTRIBUTE_TARGET)).isEqualTo("inTarget");

        final List<DomElement> outputMappings = ioMappingElement.getDomElement().getChildElementsByNameNs(ZEEBE_NAMESPACE, OUTPUT_MAPPING_ELEMENT);
        assertThat(outputMappings).isNotNull();
        assertThat(outputMappings).isNotEmpty();
        assertThat(outputMappings.get(0).getAttribute(MAPPING_ATTRIBUTE_SOURCE)).isEqualTo("outSource");
        assertThat(outputMappings.get(0).getAttribute(MAPPING_ATTRIBUTE_TARGET)).isEqualTo("outTarget");
    }


}
