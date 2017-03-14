package org.camunda.tngp.broker.workflow.graph.transformer.validator;

import static org.camunda.tngp.broker.workflow.graph.transformer.validator.ValidationCodes.NO_EXECUTABLE_PROCESS;

import java.util.Collection;

import org.camunda.bpm.model.bpmn.instance.Definitions;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class ExecutableProcessRule implements ModelElementValidator<Definitions>
{

    @Override
    public Class<Definitions> getElementType()
    {
        return Definitions.class;
    }

    @Override
    public void validate(Definitions definitionsElement, ValidationResultCollector resultCollector)
    {
        final Collection<Process> processes = definitionsElement.getChildElementsByType(Process.class);

        boolean hasExecutableProcess = false;

        for (Process process : processes)
        {
            hasExecutableProcess |= process.isExecutable();
        }

        if (!hasExecutableProcess)
        {
            resultCollector.addError(NO_EXECUTABLE_PROCESS, "Deployed BPMN model must contain at least one executable process.");
        }
    }

}
