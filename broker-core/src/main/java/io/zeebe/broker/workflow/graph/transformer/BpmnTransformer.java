package io.zeebe.broker.workflow.graph.transformer;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import org.agrona.DirectBuffer;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResults;

import io.zeebe.broker.workflow.graph.model.ExecutableWorkflow;
import io.zeebe.broker.workflow.graph.transformer.validator.ActivityIdRule;
import io.zeebe.broker.workflow.graph.transformer.validator.BpmnProcessIdRule;
import io.zeebe.broker.workflow.graph.transformer.validator.ExecutableProcessRule;
import io.zeebe.broker.workflow.graph.transformer.validator.IOMappingRule;
import io.zeebe.broker.workflow.graph.transformer.validator.OutgoingSequenceFlowRule;
import io.zeebe.broker.workflow.graph.transformer.validator.ProcessStartEventRule;
import io.zeebe.broker.workflow.graph.transformer.validator.ServiceTaskRule;
import io.zeebe.broker.workflow.graph.transformer.validator.TaskTypeRule;

public class BpmnTransformer
{
    public static final int ID_MAX_LENGTH = 255;

    private static final List<ModelElementValidator<?>> BPMN_VALIDATORS;

    static
    {
        BPMN_VALIDATORS = new ArrayList<>();
        BPMN_VALIDATORS.add(new ExecutableProcessRule());
        BPMN_VALIDATORS.add(new BpmnProcessIdRule());
        BPMN_VALIDATORS.add(new ActivityIdRule());
        BPMN_VALIDATORS.add(new ProcessStartEventRule());
        BPMN_VALIDATORS.add(new OutgoingSequenceFlowRule());
        BPMN_VALIDATORS.add(new TaskTypeRule());
        BPMN_VALIDATORS.add(new ServiceTaskRule());
        BPMN_VALIDATORS.add(new IOMappingRule());
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
