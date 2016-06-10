package org.camunda.tngp.broker.wf.repository.handler;

import java.util.Collection;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Process;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.io.DirectBufferInputStream;

/**
 * Validates whether a bpmn model can be deployed
 *
 */
public class BpmnDeploymentValidator
{
    protected String errorMessage;
    protected Process executableProcess;

    public BpmnDeploymentValidator validate(DirectBuffer resourceBuffer, int offset, int length)
    {
        BpmnModelInstance bpmnModelInstance = null;

        try
        {
            bpmnModelInstance = Bpmn.readModelFromStream(new DirectBufferInputStream(resourceBuffer, offset, length));
        }
        catch(Exception e)
        {
            errorMessage = String.format("Cannot deploy Bpmn Resource: Exception during parsing: %s", e.getMessage());
            e.printStackTrace();
        }

        if(bpmnModelInstance != null)
        {
            final Collection<Process> processes = bpmnModelInstance.getModelElementsByType(Process.class);
            for (Process process : processes)
            {
                if(process.isExecutable())
                {
                    if(executableProcess == null)
                    {
                        executableProcess = process;
                    }
                    else
                    {
                        errorMessage = "Cannot deploy Bpmn Resource: bpmn file can only contain a single executable process. Contains multiple.";
                        break;
                    }
                }
            }
            if(executableProcess == null)
            {
                errorMessage = "Cannot deploy Bpmn Resource: bpmn file does not contain any executable processes.";
            }
        }

        return this;
    }

    public String getErrorMessage()
    {
        return errorMessage;
    }

    public Process getExecutableProcess()
    {
        return executableProcess;
    }
}
