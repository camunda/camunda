package org.camunda.tngp.broker.workflow.graph.transformer;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import org.agrona.DirectBuffer;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResults;
import org.camunda.tngp.broker.workflow.graph.model.ExecutableWorkflow;
import org.camunda.tngp.broker.workflow.graph.transformer.validator.BpmnProcessIdRule;
import org.camunda.tngp.broker.workflow.graph.transformer.validator.ExecutableProcessRule;
import org.camunda.tngp.broker.workflow.graph.transformer.validator.OutgoingSequenceFlowRule;
import org.camunda.tngp.broker.workflow.graph.transformer.validator.ProcessStartEventRule;
import org.camunda.tngp.broker.workflow.graph.transformer.validator.ServiceTaskRule;
import org.camunda.tngp.broker.workflow.graph.transformer.validator.TaskTypeRule;

public class BpmnTransformer
{
    private static final List<ModelElementValidator<?>> BPMN_VALIDATORS;

    static
    {
        BPMN_VALIDATORS = new ArrayList<>();
        BPMN_VALIDATORS.add(new ExecutableProcessRule());
        BPMN_VALIDATORS.add(new BpmnProcessIdRule());
        BPMN_VALIDATORS.add(new ProcessStartEventRule());
        BPMN_VALIDATORS.add(new OutgoingSequenceFlowRule());
        BPMN_VALIDATORS.add(new TaskTypeRule());
        BPMN_VALIDATORS.add(new ServiceTaskRule());
    }

    public ValidationResults validate(DirectBuffer buffer)
    {
        return validate(readModelFromBuffer(buffer));
    }

    public ValidationResults validate(BpmnModelInstance modelInstance)
    {
        return modelInstance.validate(BPMN_VALIDATORS);
    }

    public List<ExecutableWorkflow> transform(DirectBuffer buffer)
    {
        return transform(readModelFromBuffer(buffer));
    }

    public List<ExecutableWorkflow> transform(BpmnModelInstance modelInstance)
    {
        final List<ExecutableWorkflow> transformedProcesses = new ArrayList<>();

        for (Process process : modelInstance.getModelElementsByType(Process.class))
        {
            final ExecutableWorkflow transformedProcess = transformProcess(process);

            transformedProcesses.add(transformedProcess);
        }

        return transformedProcesses;
    }

    public BpmnModelInstance readModelFromBuffer(final DirectBuffer buffer)
    {
        BpmnModelInstance bpmnModelInstance = null;

        final byte[] bytes = new byte[buffer.capacity()];
        buffer.getBytes(0, bytes);

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes))
        {
            bpmnModelInstance = Bpmn.readModelFromStream(inputStream);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to read BPMN model from buffer.", e);
        }
        return bpmnModelInstance;
    }

    private ExecutableWorkflow transformProcess(Process process)
    {
        final ExecutableWorkflow transformedWorkflow = new ExecutableWorkflow();

        Transformers.apply(process, transformedWorkflow, transformedWorkflow);

        return transformedWorkflow;
    }

}
