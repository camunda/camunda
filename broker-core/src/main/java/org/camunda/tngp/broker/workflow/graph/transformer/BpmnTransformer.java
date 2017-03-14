package org.camunda.tngp.broker.workflow.graph.transformer;

import java.util.ArrayList;
import java.util.List;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResults;
import org.camunda.tngp.broker.workflow.graph.model.ExecutableProcess;
import org.camunda.tngp.broker.workflow.graph.transformer.validator.ExecutableProcessRule;

public class BpmnTransformer
{
    private static final List<ModelElementValidator<?>> BPMN_VALIDATORS;

    static
    {
        BPMN_VALIDATORS = new ArrayList<>();
        BPMN_VALIDATORS.add(new ExecutableProcessRule());
    }

    public ValidationResults validate(BpmnModelInstance modelInstnace)
    {
        return modelInstnace.validate(BPMN_VALIDATORS);
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

    private ExecutableProcess transformProcess(Process process)
    {
        final ExecutableProcess transformedProcess = new ExecutableProcess();

        Transformers.apply(process, transformedProcess, transformedProcess);

        return transformedProcess;
    }

}
