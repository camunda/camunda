package io.zeebe.transport.impl.actor;

import java.util.concurrent.CompletableFuture;

public class ClientActorContext extends ActorContext
{
    private ClientConductor clientConductor;

    public CompletableFuture<Void> requestChannel(int streamId)
    {
        return clientConductor.requestClientChannel(streamId);
    }

    @Override
    public void setConductor(Conductor clientConductor)
    {
        super.setConductor(clientConductor);
        this.clientConductor = (ClientConductor) clientConductor;
    }

}
