package org.camunda.tngp.broker.workflow.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.model.bpmn.impl.BpmnModelConstants.BPMN20_NS;

import java.util.stream.IntStream;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Definitions;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.xml.validation.ValidationResults;
import org.camunda.tngp.broker.workflow.graph.transformer.BpmnTransformer;
import org.camunda.tngp.broker.workflow.graph.transformer.validator.ProcessIdRule;
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
            .done();

        // when
        final ValidationResults validationResults = bpmnTransformer.validate(bpmnModelInstance);

        // then
        assertThat(validationResults.hasErrors()).isTrue();
        assertThat(validationResults.getErrorCount()).isEqualTo(1);
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
        process1.builder().startEvent().done();

        final Process process2 = bpmnModelInstance.newInstance(Process.class);
        definitions.addChildElement(process2);
        process2.setExecutable(true);
        process2.setId("process2");
        process2.builder().startEvent().done();

        // when
        final ValidationResults validationResults = bpmnTransformer.validate(bpmnModelInstance);

        // then
        assertThat(validationResults.hasErrors()).isTrue();
        assertThat(validationResults.getErrorCount()).isEqualTo(1);
        assertThat(validationResults.getResults().get(bpmnModelInstance.getDefinitions()))
            .isNotNull()
            .hasSize(1)
            .extracting("code").contains(ValidationCodes.MORE_THAN_ONE_EXECUTABLE_PROCESS);
    }

    @Test
    public void shouldNotBeValidIfNoProcessId()
    {
        // given
        final BpmnModelInstance bpmnModelInstance = Bpmn.createExecutableProcess("")
            .startEvent()
            .done();

        // when
        final ValidationResults validationResults = bpmnTransformer.validate(bpmnModelInstance);

        // then
        final Process process = bpmnModelInstance.getModelElementsByType(Process.class).iterator().next();

        assertThat(validationResults.hasErrors()).isTrue();
        assertThat(validationResults.getErrorCount()).isEqualTo(1);
        assertThat(validationResults.getResults().get(process))
            .isNotNull()
            .hasSize(1)
            .extracting("code").contains(ValidationCodes.NO_PROCESS_ID);
    }

    @Test
    public void shouldNotBeValidIfProcessIdTooLong()
    {
        // given
        final StringBuffer processIdBuilder = new StringBuffer();
        IntStream.range(0, ProcessIdRule.PROCESS_ID_MAX_LENGTH + 1).forEach(i -> processIdBuilder.append("a"));
        final String processId = processIdBuilder.toString();

        final BpmnModelInstance bpmnModelInstance = Bpmn.createExecutableProcess(processId)
            .startEvent()
            .done();

        // when
        final ValidationResults validationResults = bpmnTransformer.validate(bpmnModelInstance);

        // then
        assertThat(validationResults.hasErrors()).isTrue();
        assertThat(validationResults.getErrorCount()).isEqualTo(1);
        assertThat(validationResults.getResults().get(bpmnModelInstance.getModelElementById(processId)))
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
        assertThat(validationResults.getErrorCount()).isEqualTo(1);
        assertThat(validationResults.getResults().get(bpmnModelInstance.getModelElementById("process")))
            .isNotNull()
            .hasSize(1)
            .extracting("code").contains(ValidationCodes.NO_START_EVENT);
    }


}
