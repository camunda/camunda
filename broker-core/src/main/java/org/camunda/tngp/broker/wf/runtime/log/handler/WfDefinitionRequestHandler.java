package org.camunda.tngp.broker.wf.runtime.log.handler;

import java.nio.charset.StandardCharsets;

import org.agrona.DirectBuffer;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.tngp.broker.log.LogEntryTypeHandler;
import org.camunda.tngp.broker.log.LogWriters;
import org.camunda.tngp.broker.log.ResponseControl;
import org.camunda.tngp.broker.wf.WfErrors;
import org.camunda.tngp.protocol.wf.WfDefinitionRequestReader;
import org.camunda.tngp.broker.wf.runtime.log.WfDefinitionWriter;
import org.camunda.tngp.broker.wf.runtime.request.handler.BpmnDeploymentValidator;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.protocol.error.ErrorWriter;
import org.camunda.tngp.protocol.log.WfDefinitionRequestType;
import org.camunda.tngp.protocol.wf.Constants;
import org.camunda.tngp.protocol.wf.DeployBpmnResourceAckResponse;
import org.camunda.tngp.protocol.wf.WfDefinitionReader;

public class WfDefinitionRequestHandler implements LogEntryTypeHandler<WfDefinitionRequestReader>
{

    protected LogReader logReader;
    protected IdGenerator idGenerator;

    protected ErrorWriter errorWriter = new ErrorWriter();
    protected WfDefinitionWriter wfDefinitionWriter = new WfDefinitionWriter();
    protected WfDefinitionReader wfDefinitionReader = new WfDefinitionReader();
    protected DeployBpmnResourceAckResponse responseWriter = new DeployBpmnResourceAckResponse();

    public WfDefinitionRequestHandler(
            LogReader logReader,
            IdGenerator idGenerator)
    {
        this.logReader = logReader;
        this.idGenerator = idGenerator;
    }

    @Override
    public void handle(WfDefinitionRequestReader reader, ResponseControl responseControl, LogWriters logWriters)
    {
        if (reader.type() == WfDefinitionRequestType.NEW)
        {
            final DirectBuffer resource = reader.resource();
            deployProcess(resource, 0, resource.capacity(), responseControl, logWriters);
        }
    }

    protected void deployProcess(DirectBuffer resource, int offset, int length, ResponseControl responseControl, LogWriters logWriters)
    {
        final BpmnDeploymentValidator bpmnProcessValidator = new BpmnDeploymentValidator()
                .validate(resource, offset, length);

        String errorMessage = bpmnProcessValidator.getErrorMessage();
        final Process executableProcess = bpmnProcessValidator.getExecutableProcess();

        if (executableProcess != null)
        {
            final byte[] wfDefinitionKeyBytes = executableProcess.getId().getBytes(StandardCharsets.UTF_8);

            if (wfDefinitionKeyBytes.length <= Constants.WF_DEF_KEY_MAX_LENGTH)
            {
                // TODO: hand over requestReader here
                doDeploy(wfDefinitionKeyBytes, resource, 0, resource.capacity(), responseControl, logWriters);
            }
            else
            {
                errorMessage = String.format("Id of process exceeds max length: %d.", Constants.WF_DEF_KEY_MAX_LENGTH);
            }
        }

        if (errorMessage != null)
        {
            errorWriter
                .componentCode(WfErrors.COMPONENT_CODE)
                .detailCode(WfErrors.DEPLOYMENT_ERROR)
                .errorMessage(errorMessage);

            responseControl.reject(errorWriter);
        }
    }

    protected void doDeploy(
            final byte[] wfDefinitionKeyBytes,
            DirectBuffer resource,
            final int resourceOffset,
            final int resourceLength,
            ResponseControl responseControl,
            LogWriters logWriters)
    {

        final long typeId = idGenerator.nextId();

        wfDefinitionWriter
            .id(typeId)
            .wfDefinitionKey(wfDefinitionKeyBytes)
            .resource(resource, resourceOffset, resourceLength);

        responseWriter.wfDefinitionId(typeId);

        logWriters.writeToCurrentLog(wfDefinitionWriter);
        responseControl.accept(responseWriter);
    }

}
