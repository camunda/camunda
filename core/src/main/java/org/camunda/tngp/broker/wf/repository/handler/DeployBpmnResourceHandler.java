package org.camunda.tngp.broker.wf.repository.handler;

import static java.lang.System.arraycopy;
import static java.util.Arrays.fill;

import java.nio.charset.StandardCharsets;

import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.tngp.broker.transport.worker.spi.BrokerRequestHandler;
import org.camunda.tngp.broker.wf.WfErrors;
import org.camunda.tngp.broker.wf.repository.WfRepositoryContext;
import org.camunda.tngp.broker.wf.repository.log.WfTypeReader;
import org.camunda.tngp.broker.wf.repository.log.WfTypeWriter;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogEntryReader;
import org.camunda.tngp.log.LogEntryWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.protocol.error.ErrorWriter;
import org.camunda.tngp.protocol.wf.DeployBpmnResourceAckResponse;
import org.camunda.tngp.protocol.wf.DeployBpmnResourceRequestReader;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.camunda.tngp.transport.requestresponse.server.ResponseCompletionHandler;

import uk.co.real_logic.agrona.DirectBuffer;

public class DeployBpmnResourceHandler implements BrokerRequestHandler<WfRepositoryContext>, ResponseCompletionHandler
{
    public final static int WF_TYPE_KEY_MAX_LENGTH = 256;
    protected final byte[] keyBuffer = new byte[WF_TYPE_KEY_MAX_LENGTH];

    protected LogEntryWriter logEntryWriter = new LogEntryWriter();
    protected LogEntryReader logEntryReader = new LogEntryReader(WfTypeReader.MAX_LENGTH);

    protected WfTypeWriter wfTypeWriter = new WfTypeWriter();
    protected WfTypeReader wfTypeReader = new WfTypeReader();

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

        DirectBuffer resourceBuffer = requestReader.getResource();
        final BpmnDeploymentValidator bpmnProcessValidator = new BpmnDeploymentValidator()
                .validate(resourceBuffer, 0, resourceBuffer.capacity());

        String errorMessage = bpmnProcessValidator.getErrorMessage();
        final Process executableProcess = bpmnProcessValidator.getExecutableProcess();

        if(executableProcess != null)
        {
            final byte[] wfTypeKeyBytes = executableProcess.getId().getBytes(StandardCharsets.UTF_8);

            if (wfTypeKeyBytes.length <= WF_TYPE_KEY_MAX_LENGTH)
            {
                // TODO: hand over requestReader here
                result = doDeploy(context, wfTypeKeyBytes, response, resourceBuffer, 0, resourceBuffer.capacity());
            }
            else
            {
                errorMessage = String.format("Id of process exceeds max length: %d.", WF_TYPE_KEY_MAX_LENGTH);
            }
        }

        if(errorMessage != null)
        {
            errorResponseWriter
              .componentCode(WfErrors.COMPONENT_CODE)
              .detailCode(WfErrors.DEPLOYMENT_ERROR)
              .errorMessage(errorMessage);

            if(response.allocateAndWrite(errorResponseWriter))
            {
                response.commit();
                result = 1;
            }
        }

        return result;
    }

    protected long doDeploy(
            final WfRepositoryContext context,
            final byte[] wfTypeKeyBytes,
            final DeferredResponse response, DirectBuffer msg,
            final int resourceOffset,
            final int resourceLength)
    {
        long result = -1;

        final Log wfTypeLog = context.getWfTypeLog();
        final IdGenerator wfTypeIdGenerator = context.getWfTypeIdGenerator();
        final Bytes2LongHashIndex wfTypeKeyIndex = context.getWfTypeKeyIndex().getIndex();
        final Long2LongHashIndex wfIdIndex = context.getWfTypeIdIndex().getIndex();

        final long typeId = wfTypeIdGenerator.nextId();
        responseWriter.wfTypeId(typeId);

        final int keyLength = wfTypeKeyBytes.length;
        if(keyLength <= WF_TYPE_KEY_MAX_LENGTH)
        {
            arraycopy(wfTypeKeyBytes, 0, keyBuffer, 0, keyLength);
            fill(keyBuffer, keyLength, WF_TYPE_KEY_MAX_LENGTH, (byte) 0);
        }

        int version = 0;

        final long previousVersionId = wfTypeKeyIndex.get(keyBuffer, -1);
        final long prevVersionPos = wfIdIndex.get(previousVersionId, -1);

        if(prevVersionPos != -1)
        {
            logEntryReader.read(wfTypeLog, prevVersionPos, wfTypeReader);
            version = 1 + wfTypeReader.version();
        }

        wfTypeWriter
            .id(typeId)
            .version(version)
            .resourceId(context.getResourceId())
            .prevVersionPosition(prevVersionPos)
            .wfTypeKey(wfTypeKeyBytes)
            .resource(msg, resourceOffset, resourceLength);

        if(response.allocateAndWrite(responseWriter))
        {
            final long logEntryOffset = logEntryWriter.write(wfTypeLog, wfTypeWriter);
            result = response.defer(logEntryOffset, this, null);
        }

        return result;
    }

    @Override
    public void onAsyncWorkCompleted(
            final DeferredResponse response,
            final DirectBuffer asyncWorkBuffer,
            final int offset,
            final int length,
            final Object attachement,
            final long blockPosition)
    {
        response.commit();
    }

    @Override
    public void onAsyncWorkFailed(
            final DeferredResponse response,
            final DirectBuffer asyncWorkBuffer,
            final int offset,
            final int length,
            final Object attachement)
    {
        response.abort();
    }


}
