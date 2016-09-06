package org.camunda.tngp.broker.log;

import org.camunda.tngp.protocol.error.ErrorWriter;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.camunda.tngp.util.buffer.BufferWriter;

public interface ResponseControl
{
    void accept(BufferWriter responseWriter);

    void reject(ErrorWriter errorWriter);

    ResponseControl NOOP_RESPONSE_CONTROL = new ResponseControl()
    {
        @Override
        public void accept(BufferWriter responseWriter)
        {
            // do nothing
        }

        @Override
        public void reject(ErrorWriter errorWriter)
        {
            // do nothing; could be used for detection of inconsistencies
        }
    };

    class ApiResponseControl implements ResponseControl
    {
        protected DeferredResponse response;

        public void wrap(DeferredResponse response)
        {
            this.response = response;
        }

        @Override
        public void accept(BufferWriter responseWriter)
        {
            writeResponseAndCommit(responseWriter);
        }

        @Override
        public void reject(ErrorWriter errorWriter)
        {
            writeResponseAndCommit(errorWriter);
        }

        protected void writeResponseAndCommit(BufferWriter writer)
        {
            final boolean success = response.allocateAndWrite(writer);
            // TODO: do something if it could not be allocated

            response.commit();
        }
    }
}
