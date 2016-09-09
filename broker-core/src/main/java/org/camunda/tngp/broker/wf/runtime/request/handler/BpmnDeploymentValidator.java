package org.camunda.tngp.broker.wf.runtime.request.handler;

import java.io.StringWriter;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.xml.validation.ValidationResults;
import org.camunda.tngp.bpmn.graph.validation.BpmnExecutionValidators;
import org.camunda.tngp.bpmn.graph.validation.BpmnValidationResultFormatter;

import org.agrona.DirectBuffer;
import org.agrona.io.DirectBufferInputStream;

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
        catch (Exception e)
        {
            errorMessage = String.format("Cannot deploy Bpmn Resource: Exception during parsing: %s", e.getMessage());
            e.printStackTrace();
        }

        if (bpmnModelInstance != null)
        {
            final ValidationResults results = bpmnModelInstance.validate(BpmnExecutionValidators.VALIDATORS);

            if (results.hasErrors())
            {
                final StringWriter errorMessageWriter = new StringWriter();
                results.write(errorMessageWriter, new BpmnValidationResultFormatter());
                errorMessage = errorMessageWriter.toString();
            }
            else
            {
                executableProcess = bpmnModelInstance.getModelElementsByType(Process.class).iterator().next();
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
