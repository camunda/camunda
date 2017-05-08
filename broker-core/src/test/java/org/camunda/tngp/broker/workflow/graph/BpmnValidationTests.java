package org.camunda.tngp.broker.workflow.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.model.bpmn.impl.BpmnModelConstants.BPMN20_NS;
import static org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.TASK_DEFINITION_ELEMENT;
import static org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.TASK_HEADERS_ELEMENT;
import static org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.TASK_HEADER_ELEMENT;
import static org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.TASK_RETRIES_ATTRIBUTE;
import static org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.TNGP_NAMESPACE;
import static org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.wrap;

import java.util.stream.IntStream;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.validation.ValidationResults;
import org.camunda.tngp.broker.workflow.graph.transformer.BpmnTransformer;
import org.camunda.tngp.broker.workflow.graph.transformer.validator.BpmnProcessIdRule;
import org.camunda.tngp.broker.workflow.graph.transformer.validator.ValidationCodes;
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
        final BpmnModelInstance bpmnModelInstance = Bpmn.createEmptyModel();
        final Definitions definitions = bpmnModelInstance.newInstance(Definitions.class);
        definitions.setTargetNamespace(BPMN20_NS);
        bpmnModelInstance.setDefinitions(definitions);

        final Process process1 = bpmnModelInstance.newInstance(Process.class);
        definitions.addChildElement(process1);
        process1.setExecutable(true);
        process1.setId("process1");
        process1.builder().startEvent().endEvent().done();

        final Process process2 = bpmnModelInstance.newInstance(Process.class);
        definitions.addChildElement(process2);
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
            .extracting("code").contains(ValidationCodes.NO_PROCESS_ID);
    }

    @Test
    public void shouldNotBeValidIfBpmnProcessIdTooLong()
    {
        // given
        final StringBuffer bpmnProcessIdBuilder = new StringBuffer();
        IntStream.range(0, BpmnProcessIdRule.PROCESS_ID_MAX_LENGTH + 1).forEach(i -> bpmnProcessIdBuilder.append("a"));
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
            .extracting("code").contains(ValidationCodes.PROCESS_ID_TOO_LONG);
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
        extensionElements.addExtensionElement(TNGP_NAMESPACE, TASK_DEFINITION_ELEMENT);
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
        final ModelElementInstance taskDefinition = extensionElements.addExtensionElement(TNGP_NAMESPACE, TASK_DEFINITION_ELEMENT);
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
        final ModelElementInstance taskHeaders = extensionElements.addExtensionElement(TNGP_NAMESPACE, TASK_HEADERS_ELEMENT);
        final DomElement taskHeader = bpmnModelInstance.getDocument().createElement(TNGP_NAMESPACE, TASK_HEADER_ELEMENT);
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
    public void shouldNotValidIfIOMappingIsInvalidAndProhibited()
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
