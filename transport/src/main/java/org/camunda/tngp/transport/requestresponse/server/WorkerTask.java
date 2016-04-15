package org.camunda.tngp.transport.requestresponse.server;

public interface WorkerTask<C extends AsyncRequestWorkerContext>
{

    int execute(C context);

}
