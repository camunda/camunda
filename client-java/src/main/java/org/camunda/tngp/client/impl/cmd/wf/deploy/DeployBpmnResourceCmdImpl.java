package org.camunda.tngp.client.impl.cmd.wf.deploy;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.client.cmd.DeployBpmnResourceCmd;
import org.camunda.tngp.client.cmd.DeployedWorkflowType;
import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.client.impl.cmd.AbstractCmdImpl;
import org.camunda.tngp.client.impl.cmd.ClientRequestWriter;

import uk.co.real_logic.agrona.LangUtil;

public class DeployBpmnResourceCmdImpl extends AbstractCmdImpl<DeployedWorkflowType> implements DeployBpmnResourceCmd
{
    protected final int wfRepositoryId = 0;

    protected DeployBpmnResourceRequestWriter requestWriter = new DeployBpmnResourceRequestWriter();

    public DeployBpmnResourceCmdImpl(ClientCmdExecutor cmdExecutor)
    {
        super(cmdExecutor, new DeployBpmnResourceAckResponseHandler());
    }

    @Override
    public DeployBpmnResourceCmd resourceBytes(byte[] resourceBytes)
    {
        requestWriter.resource(resourceBytes);
        return this;
    }

    @Override
    public DeployBpmnResourceCmd resourceStream(InputStream resourceStream)
    {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final byte[] readBuffer = new byte[4 * 1024];

        int bytesRead = 0;
        try
        {
            while ((bytesRead = resourceStream.read(readBuffer, 0, readBuffer.length)) > 0)
            {
                baos.write(readBuffer, 0, bytesRead);
            }
        }
        catch (IOException e)
        {
            LangUtil.rethrowUnchecked(e);
        }

        return resourceBytes(baos.toByteArray());
    }

    @Override
    public DeployBpmnResourceCmd resourceFromClasspath(String resourceName)
    {
        try (final InputStream resourceStream = getClass().getClassLoader().getResourceAsStream(resourceName))
        {
            return resourceStream(resourceStream);
        }
        catch (IOException e)
        {
            final String exceptionMsg = String.format("Cannot deploy bpmn resource from classpath: Eception while closing stream: %s", e.getMessage());
            throw new RuntimeException(exceptionMsg, e);
        }
    }

    @Override
    public DeployBpmnResourceCmd resourceFile(String filename)
    {
        try (final InputStream resourceStream = new FileInputStream(filename))
        {
            return resourceStream(resourceStream);
        }
        catch (IOException e)
        {
            final String exceptionMsg = String.format("Cannot deploy bpmn resource from classpath: %s", e.getMessage());
            throw new RuntimeException(exceptionMsg, e);
        }
    }

    @Override
    public DeployBpmnResourceCmd bpmnModelInstance(BpmnModelInstance modelInstance)
    {
        final String modelInstanceAsString = Bpmn.convertToString(modelInstance);
        return resourceBytes(modelInstanceAsString.getBytes(CHARSET));
    }

    @Override
    public ClientRequestWriter getRequestWriter()
    {
        return requestWriter;
    }

    public void setRequestWriter(DeployBpmnResourceRequestWriter requestWriter)
    {
        this.requestWriter = requestWriter;
    }

}
