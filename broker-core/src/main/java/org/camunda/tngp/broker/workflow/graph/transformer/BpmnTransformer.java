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
import org.camunda.tngp.broker.workflow.graph.model.ExecutableProcess;
import org.camunda.tngp.broker.workflow.graph.transformer.validator.BpmnProcessIdRule;
import org.camunda.tngp.broker.workflow.graph.transformer.validator.ExecutableProcessRule;
import org.camunda.tngp.broker.workflow.graph.transformer.validator.OutgoingSequenceFlowRule;
import org.camunda.tngp.broker.workflow.graph.transformer.validator.ProcessStartEventRule;

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
    }

    public ValidationResults validate(DirectBuffer buffer)
    {
        return validate(readModelFromBuffer(buffer));
    }

    public ValidationResults validate(BpmnModelInstance modelInstnace)
    {
        return modelInstnace.validate(BPMN_VALIDATORS);
    }

    public List<ExecutableProcess> transform(DirectBuffer buffer)
    {
        return transform(readModelFromBuffer(buffer));
    }

    public List<ExecutableProcess> transform(BpmnModelInstance modelInstance)
    {
        final List<ExecutableProcess> transformedProcesses = new ArrayList<>();

        for (Process process : modelInstance.getModelElementsByType(Process.class))
        {
            final ExecutableProcess transformedProcess = transformProcess(process);

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

    private ExecutableProcess transformProcess(Process process)
    {
        final ExecutableProcess transformedProcess = new ExecutableProcess();

        Transformers.apply(process, transformedProcess, transformedProcess);

        return transformedProcess;
    }

}
