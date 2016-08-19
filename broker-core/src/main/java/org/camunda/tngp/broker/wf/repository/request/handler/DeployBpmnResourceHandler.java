package org.camunda.tngp.broker.wf.repository.request.handler;

import org.camunda.tngp.broker.log.LogEntryHeaderReader.EventSource;
import org.camunda.tngp.broker.transport.worker.spi.BrokerRequestHandler;
import org.camunda.tngp.broker.wf.WfErrors;
import org.camunda.tngp.broker.wf.repository.WfRepositoryContext;
import org.camunda.tngp.broker.wf.repository.log.WfDefinitionRequestWriter;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.protocol.error.ErrorWriter;
import org.camunda.tngp.protocol.wf.DeployBpmnResourceRequestReader;
import org.camunda.tngp.taskqueue.data.WfDefinitionRequestType;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;

import org.agrona.DirectBuffer;

public class DeployBpmnResourceHandler implements BrokerRequestHandler<WfRepositoryContext>
{
    protected DeployBpmnResourceRequestReader requestReader = new DeployBpmnResourceRequestReader();

    protected final WfDefinitionRequestWriter logRequestWriter = new WfDefinitionRequestWriter();

    protected final ErrorWriter errorWriter = new ErrorWriter();

    @Override
    public long onRequest(
            final WfRepositoryContext context,
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


}
