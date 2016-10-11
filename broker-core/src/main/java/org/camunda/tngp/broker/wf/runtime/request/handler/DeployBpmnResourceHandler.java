package org.camunda.tngp.broker.wf.runtime.request.handler;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.log.LogEntryHeaderReader.EventSource;
import org.camunda.tngp.broker.log.LogWriter;
import org.camunda.tngp.broker.transport.worker.spi.BrokerRequestHandler;
import org.camunda.tngp.broker.wf.WfErrors;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeContext;
import org.camunda.tngp.broker.wf.runtime.log.WfDefinitionRequestWriter;
import org.camunda.tngp.protocol.error.ErrorWriter;
import org.camunda.tngp.protocol.wf.DeployBpmnResourceDecoder;
import org.camunda.tngp.protocol.wf.DeployBpmnResourceRequestReader;
import org.camunda.tngp.taskqueue.data.WfDefinitionRequestType;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;

public class DeployBpmnResourceHandler implements BrokerRequestHandler<WfRuntimeContext>
{
    protected DeployBpmnResourceRequestReader requestReader = new DeployBpmnResourceRequestReader();

    protected final WfDefinitionRequestWriter logRequestWriter = new WfDefinitionRequestWriter();

    protected final ErrorWriter errorWriter = new ErrorWriter();

    @Override
    public long onRequest(
            final WfRuntimeContext context,
            final DirectBuffer msg,
            final int offset,
            final int length,
            final DeferredResponse response)
    {

        requestReader.wrap(msg, offset, length);
        final DirectBuffer resource = requestReader.getResource();

        if (resource.capacity() == 0)
        {
            writeError("Deployment resource is required", response);
            return 0;
        }

        final LogWriter logWriter = context.getLogWriter();

        logRequestWriter.type(WfDefinitionRequestType.NEW)
            .resource(resource, 0, resource.capacity())
            .source(EventSource.API);

        logWriter.write(logRequestWriter);

        return response.defer();
    }

    protected void writeError(String errorMessage, DeferredResponse response)
    {
        errorWriter
            .componentCode(WfErrors.COMPONENT_CODE)
            .detailCode(WfErrors.DEPLOYMENT_REQUEST_ERROR)
            .errorMessage(errorMessage);
        response.allocateAndWrite(errorWriter);
        response.commit();
    }

    @Override
    public int getTemplateId()
    {
        return DeployBpmnResourceDecoder.TEMPLATE_ID;
    }

}
