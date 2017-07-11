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
package io.zeebe.broker.workflow.graph;

import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;

import io.zeebe.broker.workflow.graph.transformer.BpmnTransformer;
import io.zeebe.broker.workflow.graph.transformer.validator.ValidationCodes;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.validation.ValidationResults;
import org.junit.Test;

public class BpmnValidationTests
{
    private BpmnTransformer bpmnTransformer = new BpmnTransformer();

    @Test
    public void shouldBeValidProcess()
    {
        // given
        final BpmnModelInstance bpmnModelInstance = Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent()
            .done();

        // when
        final ValidationResults validationResults = bpmnTransformer.validate(bpmnModelInstance);

        // then
        assertThat(validationResults.hasErrors()).isFalse();
    }

    @Test
    public void shouldNotBeValidIfNoExecutableProcess()
    {
        // given
        final BpmnModelInstance bpmnModelInstance = Bpmn.createProcess("process")
            .startEvent()
            .endEvent()
            .done();

        // when
        final ValidationResults validationResults = bpmnTransformer.validate(bpmnModelInstance);

        // then
        assertThat(validationResults.hasErrors()).isTrue();
        assertThat(validationResults.getResults().get(bpmnModelInstance.getDefinitions()))
            .isNotNull()
            .hasSize(1)
            .extracting("code").contains(ValidationCodes.NO_EXECUTABLE_PROCESS);
    }

    @Test
    public void shouldNotBeValidIfMoreThanOneExecutableProcess()
    {
        // given
        final BpmnModelInstance bpmnModelInstance = Bpmn.createExecutableProcess("process1")
            .startEvent().endEvent().done();

        final Process process2 = bpmnModelInstance.newInstance(Process.class);
        bpmnModelInstance.getDefinitions().addChildElement(process2);
        process2.setExecutable(true);
        process2.setId("process2");
        process2.builder().startEvent().endEvent().done();

        // when
        final ValidationResults validationResults = bpmnTransformer.validate(bpmnModelInstance);

        // then
        assertThat(validationResults.hasErrors()).isTrue();
        assertThat(validationResults.getResults().get(bpmnModelInstance.getDefinitions()))
            .isNotNull()
            .hasSize(1)
            .extracting("code").contains(ValidationCodes.MORE_THAN_ONE_EXECUTABLE_PROCESS);
    }

    @Test
    public void shouldNotBeValidIfNoBpmnProcessId()
    {
        // given
        final BpmnModelInstance bpmnModelInstance = Bpmn.createExecutableProcess("")
            .startEvent()
            .endEvent()
            .done();

        // when
        final ValidationResults validationResults = bpmnTransformer.validate(bpmnModelInstance);

        // then
        final Process process = bpmnModelInstance.getModelElementsByType(Process.class).iterator().next();

        assertThat(validationResults.hasErrors()).isTrue();
        assertThat(validationResults.getResults().get(process))
            .isNotNull()
            .hasSize(1)
            .extracting("code").contains(ValidationCodes.MISSING_ID);
    }

    @Test
    public void shouldNotBeValidIfBpmnProcessIdTooLong()
    {
        // given
        final StringBuffer bpmnProcessIdBuilder = new StringBuffer();
        IntStream.range(0, BpmnTransformer.ID_MAX_LENGTH + 1).forEach(i -> bpmnProcessIdBuilder.append("a"));
        final String bpmnProcessId = bpmnProcessIdBuilder.toString();

        final BpmnModelInstance bpmnModelInstance = Bpmn.createExecutableProcess(bpmnProcessId)
            .startEvent()
            .endEvent()
            .done();

        // when
        final ValidationResults validationResults = bpmnTransformer.validate(bpmnModelInstance);

        // then
        assertThat(validationResults.hasErrors()).isTrue();
        assertThat(validationResults.getResults().get(bpmnModelInstance.getModelElementById(bpmnProcessId)))
            .isNotNull()
            .hasSize(1)
            .extracting("code").contains(ValidationCodes.ID_TOO_LONG);
    }

    @Test
    public void shouldNotBeValidIfActivityIdTooLong()
    {
        // given
        final StringBuffer bpmnProcessIdBuilder = new StringBuffer();
        IntStream.range(0, BpmnTransformer.ID_MAX_LENGTH + 1).forEach(i -> bpmnProcessIdBuilder.append("a"));
        final String activityId = bpmnProcessIdBuilder.toString();

        final BpmnModelInstance bpmnModelInstance = Bpmn.createExecutableProcess("process")
            .startEvent(activityId)
            .endEvent()
            .done();

        // when
        final ValidationResults validationResults = bpmnTransformer.validate(bpmnModelInstance);

        // then
        assertThat(validationResults.hasErrors()).isTrue();
        assertThat(validationResults.getResults().get(bpmnModelInstance.getModelElementById(activityId)))
            .isNotNull()
            .hasSize(1)
            .extracting("code").contains(ValidationCodes.ID_TOO_LONG);
    }

    @Test
    public void shouldNotBeValidIfNoStartEvent()
    {
        // given
        final BpmnModelInstance bpmnModelInstance = Bpmn.createExecutableProcess("process")
            .done();

        // when
        final ValidationResults validationResults = bpmnTransformer.validate(bpmnModelInstance);

        // then
        assertThat(validationResults.hasErrors()).isTrue();
        assertThat(validationResults.getResults().get(bpmnModelInstance.getModelElementById("process")))
            .isNotNull()
            .hasSize(1)
            .extracting("code").contains(ValidationCodes.NO_START_EVENT);
    }

    @Test
    public void shouldNotBeValidIfNoNoneStartEvent()
    {
        // given
        final BpmnModelInstance bpmnModelInstance = Bpmn.createExecutableProcess("process")
                .startEvent("foo")
                    .message("bar")
                .endEvent()
                .done();

        // when
        final ValidationResults validationResults = bpmnTransformer.validate(bpmnModelInstance);

        // then
        assertThat(validationResults.hasErrors()).isTrue();
        assertThat(validationResults.getResults().get(bpmnModelInstance.getModelElementById("process")))
            .isNotNull()
            .extracting("code").contains(ValidationCodes.NO_START_EVENT);
    }

    @Test
    public void shouldNotBeValidIfMoreThanOneNoneStartEvent()
    {
        // given
        final BpmnModelInstance bpmnModelInstance = Bpmn.createExecutableProcess("process").done();

        final Process process = bpmnModelInstance.getModelElementById("process");

        final StartEvent startEvent1 = bpmnModelInstance.newInstance(StartEvent.class);
        process.addChildElement(startEvent1);
        startEvent1.builder().endEvent().done();

        final StartEvent startEvent2 = bpmnModelInstance.newInstance(StartEvent.class);
        process.addChildElement(startEvent2);
        startEvent2.builder().endEvent().done();

        // when
        final ValidationResults validationResults = bpmnTransformer.validate(bpmnModelInstance);

        // then
        assertThat(validationResults.hasErrors()).isTrue();
        assertThat(validationResults.getResults().get(bpmnModelInstance.getModelElementById("process")))
            .isNotNull()
            .hasSize(1)
            .extracting("code").contains(ValidationCodes.MORE_THAN_ONE_NONE_START_EVENT);
    }

    @Test
    public void shouldNotBeValidIfMoreThanOneOutgoingSequenceFlow()
    {
        // given
        final BpmnModelInstance bpmnModelInstance = Bpmn.createExecutableProcess("process")
                .startEvent("foo")
                    .sequenceFlowId("a")
                    .endEvent()
                .moveToNode("foo")
                    .sequenceFlowId("b")
                    .endEvent()
                .done();

        // when
        final ValidationResults validationResults = bpmnTransformer.validate(bpmnModelInstance);

        // then
        assertThat(validationResults.hasErrors()).isTrue();
        assertThat(validationResults.getResults().get(bpmnModelInstance.getModelElementById("foo")))
            .isNotNull()
            .extracting("code").contains(ValidationCodes.MORE_THAN_ONE_OUTGOING_SEQUENCE_FLOW);
    }

    @Test
    public void shouldNotBeValidIfEndEventHasAnOutgoingSequenceFlow()
    {
        // given
        final BpmnModelInstance bpmnModelInstance = Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent("foo")
            .userTask()
            .endEvent()
            .done();

        // when
        final ValidationResults validationResults = bpmnTransformer.validate(bpmnModelInstance);

        // then
        assertThat(validationResults.hasErrors()).isTrue();
        assertThat(validationResults.getResults().get(bpmnModelInstance.getModelElementById("foo")))
            .isNotNull()
            .extracting("code").contains(ValidationCodes.OUTGOING_SEQUENCE_FLOW_AT_END_EVENT);
    }

    @Test
    public void shouldNotBeValidIfTaskTypeIsNotServiceTask()
    {
        // given
        final BpmnModelInstance bpmnModelInstance = Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("foo")
            .endEvent()
            .done();

        // when
        final ValidationResults validationResults = bpmnTransformer.validate(bpmnModelInstance);

        // then
        assertThat(validationResults.hasErrors()).isTrue();
        assertThat(validationResults.getResults().get(bpmnModelInstance.getModelElementById("foo")))
            .isNotNull()
            .extracting("code").contains(ValidationCodes.NOT_SUPPORTED_TASK_TYPE);
    }

    @Test
    public void shouldNotBeValidIfServiceTaskHasNoTaskDefinition()
    {
        // given
        final BpmnModelInstance bpmnModelInstance = wrap(Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("foo")
            .endEvent()
            .done());

        // when
        final ValidationResults validationResults = bpmnTransformer.validate(bpmnModelInstance);

        // then
        assertThat(validationResults.hasErrors()).isTrue();
        assertThat(validationResults.getResults().get(bpmnModelInstance.getModelElementById("foo")))
            .isNotNull()
            .extracting("code").contains(ValidationCodes.NO_TASK_DEFINITION);
    }

    @Test
    public void shouldNotBeValidIfServiceTaskHasNoTaskType()
    {
        // given
        final BpmnModelInstance bpmnModelInstance = wrap(Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("foo")
            .endEvent()
            .done());

        final ModelElementInstance taskElement = bpmnModelInstance.getModelElementById("foo");
        final ExtensionElements extensionElements = bpmnModelInstance.newInstance(ExtensionElements.class);
        extensionElements.addExtensionElement(ZEEBE_NAMESPACE, TASK_DEFINITION_ELEMENT);
        taskElement.addChildElement(extensionElements);

        // when
        final ValidationResults validationResults = bpmnTransformer.validate(bpmnModelInstance);

        // then
        assertThat(validationResults.hasErrors()).isTrue();
        assertThat(validationResults.getResults().get(bpmnModelInstance.getModelElementById("foo")))
            .isNotNull()
            .extracting("code").contains(ValidationCodes.NO_TASK_TYPE);
    }

    @Test
    public void shouldNotBeValidIfServiceTaskHasInvalidTaskRetries()
    {
        // given
        final BpmnModelInstance bpmnModelInstance = wrap(Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("foo")
            .endEvent()
            .done());

        final ExtensionElements extensionElements = getExtensionElements(bpmnModelInstance, "foo");
        final ModelElementInstance taskDefinition = extensionElements.addExtensionElement(ZEEBE_NAMESPACE, TASK_DEFINITION_ELEMENT);
        taskDefinition.setAttributeValue(TASK_RETRIES_ATTRIBUTE, "bar");

        // when
        final ValidationResults validationResults = bpmnTransformer.validate(bpmnModelInstance);

        // then
        assertThat(validationResults.hasErrors()).isTrue();
        assertThat(validationResults.getResults().get(bpmnModelInstance.getModelElementById("foo")))
            .isNotNull()
            .extracting("code").contains(ValidationCodes.INVALID_TASK_RETRIES);
    }

    @Test
    public void shouldNotBeValidIfServiceTaskHasInvalidTaskHeader()
    {
        // given
        final BpmnModelInstance bpmnModelInstance = wrap(Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("foo")
            .endEvent()
            .done());

        final ExtensionElements extensionElements = getExtensionElements(bpmnModelInstance, "foo");
        final ModelElementInstance taskHeaders = extensionElements.addExtensionElement(ZEEBE_NAMESPACE, TASK_HEADERS_ELEMENT);
        final DomElement taskHeader = bpmnModelInstance.getDocument().createElement(ZEEBE_NAMESPACE, TASK_HEADER_ELEMENT);
        taskHeaders.getDomElement().appendChild(taskHeader);

        // when
        final ValidationResults validationResults = bpmnTransformer.validate(bpmnModelInstance);

        // then
        assertThat(validationResults.hasErrors()).isTrue();
        assertThat(validationResults.getResults().get(bpmnModelInstance.getModelElementById("foo")))
            .isNotNull()
            .extracting("code").contains(ValidationCodes.NO_TASK_HEADER_KEY, ValidationCodes.NO_TASK_HEADER_VALUE);
    }

    @Test
    public void shouldNotValidIfIOMappingIsInvalid()
    {
        // given
        final BpmnModelInstance bpmnModelInstance = wrap(Bpmn.createExecutableProcess()
            .startEvent()
            .serviceTask("foo")
                .name("bar")
            .endEvent()
            .done())
            .taskDefinition("foo", "test", 4)
            .ioMapping("foo")
                .input("foo", "$")
            .done();

        // when
        final ValidationResults validationResults = bpmnTransformer.validate(bpmnModelInstance);

        // then
        assertThat(validationResults.hasErrors()).isTrue();
        assertThat(validationResults.getResults().get(getActivityExtensionElements(bpmnModelInstance, "foo")))
            .isNotNull()
            .extracting("code").contains(ValidationCodes.INVALID_JSON_PATH_EXPRESSION);
    }


    @Test
    public void shouldNotTransformForProhibitedTaskMapping()
    {
        // given
        final BpmnModelInstance bpmnModelInstance = wrap(Bpmn.createExecutableProcess()
            .startEvent()
            .serviceTask("foo")
            .name("bar")
            .done())
            .taskDefinition("foo", "test", 4)
            .ioMapping("foo")
            .input("$.*", "$")
            .done();

        // when
        final ValidationResults validationResults = bpmnTransformer.validate(bpmnModelInstance);

        // then
        assertThat(validationResults.hasErrors()).isTrue();
        assertThat(validationResults.getResults().get(getActivityExtensionElements(bpmnModelInstance, "foo")))
            .isNotNull()
            .extracting("code").contains(ValidationCodes.PROHIBITED_JSON_PATH_EXPRESSION);
    }

    @Test
    public void shouldNotTransformForOtherProhibitedTaskMapping()
    {
        // given
        final BpmnModelInstance bpmnModelInstance = wrap(Bpmn.createExecutableProcess()
            .startEvent()
            .serviceTask("foo")
            .name("bar")
            .done())
            .taskDefinition("foo", "test", 4)
            .ioMapping("foo")
                .input("$.[foo,bar]", "$")
            .done();

        // when
        final ValidationResults validationResults = bpmnTransformer.validate(bpmnModelInstance);

        // then
        assertThat(validationResults.hasErrors()).isTrue();
        assertThat(validationResults.getResults().get(getActivityExtensionElements(bpmnModelInstance, "foo")))
            .isNotNull()
            .extracting("code").contains(ValidationCodes.PROHIBITED_JSON_PATH_EXPRESSION);
    }

    @Test
    public void shouldNotValidtIfIOMappingIsInvalidAndProhibited()
    {
        // given
        final BpmnModelInstance bpmnModelInstance = wrap(Bpmn.createExecutableProcess()
                                                             .startEvent()
                                                             .serviceTask("foo")
                                                             .name("bar")
                                                             .endEvent()
                                                             .done())
            .taskDefinition("foo", "test", 4)
            .ioMapping("foo")
            .input("foo", "$")
            .output("$.*", "$")
            .done();

        // when
        final ValidationResults validationResults = bpmnTransformer.validate(bpmnModelInstance);

        // then
        assertThat(validationResults.hasErrors()).isTrue();
        assertThat(validationResults.getResults().get(getActivityExtensionElements(bpmnModelInstance, "foo")))
            .isNotNull()
            .extracting("code")
            .contains(ValidationCodes.INVALID_JSON_PATH_EXPRESSION,
                      ValidationCodes.PROHIBITED_JSON_PATH_EXPRESSION);
    }

    @Test
    public void shouldNotValidIfIOTargetMappingIsInvalidAndProhibited()
    {
        // given
        final BpmnModelInstance bpmnModelInstance = wrap(Bpmn.createExecutableProcess()
            .startEvent()
            .serviceTask("foo")
            .name("bar")
            .endEvent()
            .done())
            .taskDefinition("foo", "test", 4)
            .ioMapping("foo")
                .input("$", "foo")
                .output("$", "$.*")
            .done();

        // when
        final ValidationResults validationResults = bpmnTransformer.validate(bpmnModelInstance);

        // then
        assertThat(validationResults.hasErrors()).isTrue();
        assertThat(validationResults.getResults().get(getActivityExtensionElements(bpmnModelInstance, "foo")))
            .isNotNull()
            .extracting("code")
            .contains(ValidationCodes.INVALID_JSON_PATH_EXPRESSION,
                      ValidationCodes.PROHIBITED_JSON_PATH_EXPRESSION);
    }

    @Test
    public void shouldNotValidIfRootAndOtherMappingIsUsed()
    {
        // given
        final BpmnModelInstance bpmnModelInstance = wrap(Bpmn.createExecutableProcess()
            .startEvent()
            .serviceTask("foo")
            .name("bar")
            .endEvent()
            .done())
            .taskDefinition("foo", "test", 4)
            .ioMapping("foo")
            .input("$.obj", "$.obj")
            .input("$.foo", "$")
            .done();

        // when
        final ValidationResults validationResults = bpmnTransformer.validate(bpmnModelInstance);

        // then
        assertThat(validationResults.hasErrors()).isTrue();
        assertThat(validationResults.getResults().get(getActivityExtensionElements(bpmnModelInstance, "foo")))
            .isNotNull()
            .extracting("code")
            .contains(ValidationCodes.REDUNDANT_MAPPING);
    }

    private ExtensionElements getActivityExtensionElements(BpmnModelInstance bpmnModelInstance, String name)
    {
        return ((BaseElement) bpmnModelInstance.getModelElementById(name)).getExtensionElements();
    }

    private ExtensionElements getExtensionElements(final BpmnModelInstance bpmnModelInstance, String activityId)
    {
        final ExtensionElements extensionElements = bpmnModelInstance.newInstance(ExtensionElements.class);

        final ModelElementInstance element = bpmnModelInstance.getModelElementById(activityId);
        element.addChildElement(extensionElements);

        return extensionElements;
    }

}
