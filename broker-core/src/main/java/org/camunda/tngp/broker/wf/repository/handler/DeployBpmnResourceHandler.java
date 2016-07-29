package org.camunda.tngp.broker.wf.repository.handler;

import java.nio.charset.StandardCharsets;

import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.tngp.broker.transport.worker.spi.BrokerRequestHandler;
import org.camunda.tngp.broker.wf.WfErrors;
import org.camunda.tngp.broker.wf.repository.WfRepositoryContext;
import org.camunda.tngp.broker.wf.repository.log.WfDefinitionReader;
import org.camunda.tngp.broker.wf.repository.log.WfDefinitionWriter;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogEntryReader;
import org.camunda.tngp.log.LogEntryWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.protocol.error.ErrorWriter;
import org.camunda.tngp.protocol.wf.Constants;
import org.camunda.tngp.protocol.wf.DeployBpmnResourceAckResponse;
import org.camunda.tngp.protocol.wf.DeployBpmnResourceRequestReader;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.camunda.tngp.transport.requestresponse.server.ResponseCompletionHandler;

import uk.co.real_logic.agrona.DirectBuffer;

public class DeployBpmnResourceHandler implements BrokerRequestHandler<WfRepositoryContext>, ResponseCompletionHandler
{
    protected LogEntryWriter logEntryWriter = new LogEntryWriter();
    protected LogEntryReader logEntryReader = new LogEntryReader(WfDefinitionReader.MAX_LENGTH);

    protected WfDefinitionWriter wfDefinitionWriter = new WfDefinitionWriter();
    protected WfDefinitionReader wfDefinitionReader = new WfDefinitionReader();

    protected DeployBpmnResourceAckResponse responseWriter = new DeployBpmnResourceAckResponse();
    protected ErrorWriter errorResponseWriter = new ErrorWriter();

    protected final DeployBpmnResourceRequestReader requestReader = new DeployBpmnResourceRequestReader();

    @Override
    public long onRequest(
            final WfRepositoryContext context,
            final DirectBuffer msg,
            final int offset,
            final int length,
            final DeferredResponse response)
    {
        long result = -1;

        requestReader.wrap(msg, offset, length);

        final DirectBuffer resourceBuffer = requestReader.getResource();
        final BpmnDeploymentValidator bpmnProcessValidator = new BpmnDeploymentValidator()
                .validate(resourceBuffer, 0, resourceBuffer.capacity());

        String errorMessage = bpmnProcessValidator.getErrorMessage();
        final Process executableProcess = bpmnProcessValidator.getExecutableProcess();

        if (executableProcess != null)
        {
            final byte[] wfDefinitionKeyBytes = executableProcess.getId().getBytes(StandardCharsets.UTF_8);

            if (wfDefinitionKeyBytes.length <= Constants.WF_DEF_KEY_MAX_LENGTH)
            {
                // TODO: hand over requestReader here
                result = doDeploy(context, wfDefinitionKeyBytes, response, resourceBuffer, 0, resourceBuffer.capacity());
            }
            else
            {
                errorMessage = String.format("Id of process exceeds max length: %d.", Constants.WF_DEF_KEY_MAX_LENGTH);
            }
        }

        if (errorMessage != null)
        {
            errorResponseWriter
                .componentCode(WfErrors.COMPONENT_CODE)
                .detailCode(WfErrors.DEPLOYMENT_ERROR)
                .errorMessage(errorMessage);

            if (response.allocateAndWrite(errorResponseWriter))
            {
                response.commit();
                result = 1;
            }
        }

        return result;
    }

    protected long doDeploy(
            final WfRepositoryContext context,
            final byte[] wfDefinitionKeyBytes,
            final DeferredResponse response,
            final DirectBuffer msg,
            final int resourceOffset,
            final int resourceLength)
    {
        long result = -1;

        final Log wfDefinitionLog = context.getWfDefinitionLog();
        final IdGenerator wfDefinitionIdGenerator = context.getWfDefinitionIdGenerator();
        final Bytes2LongHashIndex wfDefinitionKeyIndex = context.getWfDefinitionKeyIndex().getIndex();
        final Long2LongHashIndex wfIdIndex = context.getWfDefinitionIdIndex().getIndex();

        final long typeId = wfDefinitionIdGenerator.nextId();
        responseWriter.wfDefinitionId(typeId);

        int version = 0;

        final long previousVersionId = wfDefinitionKeyIndex.get(wfDefinitionKeyBytes, -1);
        final long prevVersionPos = wfIdIndex.get(previousVersionId, -1);

        if (prevVersionPos != -1)
        {
            logEntryReader.read(wfDefinitionLog, prevVersionPos, wfDefinitionReader);
            version = 1 + wfDefinitionReader.version();
        }

        wfDefinitionWriter
            .id(typeId)
            .version(version)
            .resourceId(context.getResourceId())
            .prevVersionPosition(prevVersionPos)
            .wfDefinitionKey(wfDefinitionKeyBytes)
            .resource(msg, resourceOffset, resourceLength);

        if (response.allocateAndWrite(responseWriter))
        {
            final long logEntryOffset = logEntryWriter.write(wfDefinitionLog, wfDefinitionWriter);
            result = response.defer(logEntryOffset, this);
        }

        return result;
    }

    @Override
    public void onAsyncWorkCompleted(final DeferredResponse response)
    {
        response.commit();
    }

    @Override
    public void onAsyncWorkFailed(final DeferredResponse response)
    {
        response.abort();
    }


}
