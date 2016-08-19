package org.camunda.tngp.broker.wf.runtime.request.handler;

import org.camunda.tngp.broker.log.LogEntryHeaderReader.EventSource;
import org.camunda.tngp.broker.transport.worker.spi.BrokerRequestHandler;
import org.camunda.tngp.broker.wf.WfErrors;
import org.camunda.tngp.broker.wf.runtime.StartWorkflowInstanceRequestReader;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeContext;
import org.camunda.tngp.broker.wf.runtime.log.WorkflowInstanceRequestWriter;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.protocol.error.ErrorWriter;
import org.camunda.tngp.protocol.wf.Constants;
import org.camunda.tngp.protocol.wf.runtime.StartWorkflowInstanceDecoder;
import org.camunda.tngp.taskqueue.data.ProcessInstanceRequestType;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;

import org.agrona.DirectBuffer;

public class StartWorkflowInstanceHandler implements BrokerRequestHandler<WfRuntimeContext>
{

    protected StartWorkflowInstanceRequestReader requestReader = new StartWorkflowInstanceRequestReader();

    protected WorkflowInstanceRequestWriter logRequestWriter = new WorkflowInstanceRequestWriter();

    protected final ErrorWriter errorWriter = new ErrorWriter();

    @Override
    public long onRequest(
            final WfRuntimeContext context,
            final DirectBuffer msg,
            final int offset,
            final int length,
            final DeferredResponse response)
    {
        System.out.println();
        System.out.println("Starting process instance");

        requestReader.wrap(msg, offset, length);

        final DirectBuffer wfDefinitionKey = requestReader.wfDefinitionKey();
        final long wfDefinitionId = requestReader.wfDefinitionId();

        if (wfDefinitionId == StartWorkflowInstanceDecoder.wfDefinitionIdNullValue() && wfDefinitionKey.capacity() == 0)
        {
            writeError("Either workflow definition id or key must be specified", response);
            return 0;
        }

        if (wfDefinitionId != StartWorkflowInstanceDecoder.wfDefinitionIdNullValue() && wfDefinitionKey.capacity() > 0)
        {
            writeError("Only one parameter, workflow definition id or key, can be specified", response);
            return 0;
        }

        if (wfDefinitionKey.capacity() > Constants.WF_DEF_KEY_MAX_LENGTH)
        {
            writeError("Workflow definition key must not be longer than " + Constants.WF_DEF_KEY_MAX_LENGTH + " bytes", response);
            return 0;
        }

        final LogWriter logWriter = context.getLogWriter();

        logRequestWriter.type(ProcessInstanceRequestType.NEW)
            .wfDefinitionId(wfDefinitionId)
            .wfDefinitionKey(wfDefinitionKey, 0, wfDefinitionKey.capacity())
            .source(EventSource.API);

        logWriter.write(logRequestWriter);
        return response.defer();
    }

    protected void writeError(String errorMessage, DeferredResponse response)
    {
        errorWriter
            .componentCode(WfErrors.COMPONENT_CODE)
            .detailCode(WfErrors.PROCESS_INSTANCE_REQUEST_ERROR)
            .errorMessage(errorMessage);

        response.allocateAndWrite(errorWriter);
        response.commit();
    }

}
