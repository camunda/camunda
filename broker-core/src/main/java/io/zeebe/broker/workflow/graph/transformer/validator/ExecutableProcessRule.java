package io.zeebe.broker.workflow.graph.transformer.validator;

import static io.zeebe.broker.workflow.graph.transformer.validator.ValidationCodes.MORE_THAN_ONE_EXECUTABLE_PROCESS;
import static io.zeebe.broker.workflow.graph.transformer.validator.ValidationCodes.NO_EXECUTABLE_PROCESS;

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

        int executableProcesses = 0;

        for (Process process : processes)
        {
            if (process.isExecutable())
            {
                executableProcesses += 1;
            }
        }

        if (executableProcesses == 0)
        {
            resultCollector.addError(NO_EXECUTABLE_PROCESS, "BPMN model must contain at least one executable process.");
        }
        else if (executableProcesses > 1)
        {
            resultCollector.addError(MORE_THAN_ONE_EXECUTABLE_PROCESS, "BPMN model must not contain more than one executable process.");
        }
    }

}
