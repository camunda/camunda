package org.camunda.tngp.broker.wf.repository.handler;

import static java.lang.System.arraycopy;
import static java.util.Arrays.fill;

import java.nio.charset.StandardCharsets;
import java.util.Collection;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.tngp.broker.transport.worker.spi.BrokerRequestHandler;
import org.camunda.tngp.broker.wf.repository.WfRepositoryContext;
import org.camunda.tngp.broker.wf.repository.WfTypeReader;
import org.camunda.tngp.dispatcher.ClaimedFragment;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.protocol.wf.DeployBpmnResourceAckEncoder;
import org.camunda.tngp.protocol.wf.DeployBpmnResourceDecoder;
import org.camunda.tngp.protocol.wf.DeployBpmnResourceNackEncoder;
import org.camunda.tngp.taskqueue.data.MessageHeaderEncoder;
import org.camunda.tngp.taskqueue.data.WfTypeDecoder;
import org.camunda.tngp.taskqueue.data.WfTypeEncoder;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.camunda.tngp.transport.requestresponse.server.ResponseCompletionHandler;

import uk.co.real_logic.agrona.BitUtil;
import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;
import uk.co.real_logic.agrona.io.DirectBufferInputStream;

public class DeployBpmnResourceHandler implements BrokerRequestHandler<WfRepositoryContext>, ResponseCompletionHandler
{
    public final static int WF_TYPE_KEY_MAX_LENGTH = 256;
    protected final byte[] keyBuffer = new byte[WF_TYPE_KEY_MAX_LENGTH];

    protected final DeployBpmnResourceDecoder requestDecoder = new DeployBpmnResourceDecoder();
    protected final WfTypeEncoder wfTypeEncoder = new WfTypeEncoder();
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final DeployBpmnResourceAckEncoder ackResponseEncoder = new DeployBpmnResourceAckEncoder();
    protected final DeployBpmnResourceNackEncoder nackResponseEncoder = new DeployBpmnResourceNackEncoder();
    protected final ClaimedFragment claimedLogEntry = new ClaimedFragment();
    protected final WfTypeReader prevVersionReader = new WfTypeReader();

    @Override
    public long onRequest(
            final WfRepositoryContext context,
            final DirectBuffer msg,
            final int offset,
            final int length,
            final DeferredResponse response,
            final int sbeBlockLength,
            final int sbeSchemaVersion)
    {
        final Log wfTypeLog = context.getWfTypeLog();
        final Dispatcher logWriteBuffer = wfTypeLog.getWriteBuffer();
        final IdGenerator wfTypeIdGenerator = context.getWfTypeIdGenerator();
        final Bytes2LongHashIndex wfTypeKeyIndex = context.getWfTypeKeyIndex().getIndex();

        requestDecoder.wrap(msg, offset, sbeBlockLength, sbeSchemaVersion);

        final int resourceLength = requestDecoder.resourceLength();
        final int resourceOffset = requestDecoder.limit() + DeployBpmnResourceDecoder.resourceHeaderLength();
        final Process executableProcess = parseExecutableProcess(msg, resourceLength, resourceOffset, response, context);

        if(executableProcess != null)
        {
            final long typeId = wfTypeIdGenerator.nextId();
            final byte[] wfTypeKeyBytes = executableProcess.getId().getBytes(StandardCharsets.UTF_8);

            int version = 0;

            final int keyLength = wfTypeKeyBytes.length;
            if (keyLength <= WF_TYPE_KEY_MAX_LENGTH)
            {
                arraycopy(wfTypeKeyBytes, 0, keyBuffer, 0, keyLength);
                fill(keyBuffer, keyLength, WF_TYPE_KEY_MAX_LENGTH, (byte) 0);
            }
            else
            {
                // TODO: max key length exceeded
            }

            long prevVersionPos = wfTypeKeyIndex.get(keyBuffer, WfTypeEncoder.prevVersionPositionNullValue());
            if(WfTypeEncoder.prevVersionPositionNullValue() != prevVersionPos)
            {
                wfTypeLog.pollFragment(prevVersionPos, prevVersionReader);
                final WfTypeDecoder prevVersionDecoder = prevVersionReader.getDecoder();
                version = 1 + prevVersionDecoder.version();
            }

            final int ackResponseLength = MessageHeaderEncoder.ENCODED_LENGTH + ackResponseEncoder.sbeBlockLength();

            if(response.allocate(ackResponseLength))
            {
                final long claimedOffset = claimLogEntry(resourceLength, logWriteBuffer, wfTypeKeyBytes);

                if(claimedOffset >= 0)
                {
                    final MutableDirectBuffer claimedBuffer = claimedLogEntry.getBuffer();

                    int writeOffset = claimedLogEntry.getOffset();

                    headerEncoder.wrap(claimedBuffer, writeOffset)
                        .blockLength(wfTypeEncoder.sbeBlockLength())
                        .templateId(wfTypeEncoder.sbeTemplateId())
                        .schemaId(wfTypeEncoder.sbeSchemaId())
                        .version(wfTypeEncoder.sbeSchemaVersion())
                        .resourceId(context.getResourceId());

                    writeOffset += headerEncoder.encodedLength();

                    wfTypeEncoder.wrap(claimedBuffer, writeOffset)
                        .id(typeId)
                        .version(version)
                        .prevVersionPosition(prevVersionPos)
                        .putTypeKey(wfTypeKeyBytes, 0, wfTypeKeyBytes.length)
                        .putResource(msg, resourceOffset, resourceLength);

                    claimedLogEntry.commit();

                    final MutableDirectBuffer responseBuffer = response.getBuffer();

                    writeOffset = response.getClaimedOffset();

                    headerEncoder.wrap(responseBuffer, writeOffset)
                        .blockLength(ackResponseEncoder.sbeBlockLength())
                        .templateId(ackResponseEncoder.sbeTemplateId())
                        .schemaId(ackResponseEncoder.sbeSchemaId())
                        .version(ackResponseEncoder.sbeSchemaVersion())
                        .resourceId(context.getResourceId());

                    writeOffset += headerEncoder.encodedLength();

                    ackResponseEncoder.wrap(responseBuffer, writeOffset)
                        .wfTypeId(typeId);

                    response.defer(claimedOffset, this, null);
                }
                else
                {
                    // ERROR: backpressured by log buffer
                }
            }
            else
            {
                // ERROR: cannot allocate response
            }
        }

        return 1;
    }

    private Process parseExecutableProcess(
            final DirectBuffer msg,
            final int resourceLength,
            final int resourceOffset,
            final DeferredResponse response,
            final WfRepositoryContext ctx) {

        String errorMessage = null;
        Process executableProcess = null;
        BpmnModelInstance bpmnModelInstance = null;

        try
        {
            bpmnModelInstance = Bpmn.readModelFromStream(new DirectBufferInputStream(msg, resourceOffset, resourceLength));
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
                errorMessage = "Cannot deploy Bpmn Resource: bpmn file does not contain any executable brocesses.";
            }
        }

        if(errorMessage != null)
        {
            executableProcess = null;

            final byte[] errorMessageBytes = errorMessage.getBytes(StandardCharsets.UTF_8);

            final int errorResponseLength = MessageHeaderEncoder.ENCODED_LENGTH
                    + DeployBpmnResourceNackEncoder.BLOCK_LENGTH
                    + DeployBpmnResourceNackEncoder.errorMessageHeaderLength()
                    + errorMessageBytes.length;

            if(response.allocate(errorResponseLength))
            {
                final MutableDirectBuffer responseBuffer = response.getBuffer();
                int writeOffset = response.getClaimedOffset();

                headerEncoder.wrap(responseBuffer, writeOffset)
                    .blockLength(nackResponseEncoder.sbeBlockLength())
                    .templateId(nackResponseEncoder.sbeTemplateId())
                    .schemaId(nackResponseEncoder.sbeSchemaId())
                    .version(nackResponseEncoder.sbeSchemaVersion())
                    .resourceId(ctx.getResourceId());

                writeOffset += headerEncoder.encodedLength();

                nackResponseEncoder.wrap(responseBuffer, writeOffset)
                    .putErrorMessage(errorMessageBytes, 0, errorMessageBytes.length);

                response.commit();
            }
            else
            {
                // ERROR: cannot allocate response
            }
        }

        return executableProcess;
    }

    private long claimLogEntry(final int resourceLength, final Dispatcher logWriteBuffer, final byte[] wfTypeKeyBytes)
    {
        final int logEntryLength = MessageHeaderEncoder.ENCODED_LENGTH
                + WfTypeEncoder.BLOCK_LENGTH
                + WfTypeEncoder.typeKeyHeaderLength()
                + wfTypeKeyBytes.length
                + WfTypeEncoder.resourceHeaderLength()
                + resourceLength;

        long claimedOffset = -1;
        do
        {
            claimedOffset = logWriteBuffer.claim(claimedLogEntry, logEntryLength);
        }
        while(claimedOffset == -2);

        claimedOffset -= BitUtil.align(claimedLogEntry.getFragmentLength(), 8);

        return claimedOffset;
    }

    @Override
    public void onAsyncWorkCompleted(DeferredResponse response, DirectBuffer asyncWorkBuffer, int offset, int length,
            Object attachement, long blockPosition)
    {
        response.commit();
    }

    @Override
    public void onAsyncWorkFailed(DeferredResponse response, DirectBuffer asyncWorkBuffer, int offset, int length,
            Object attachement)
    {
        response.abort();
    }


}
