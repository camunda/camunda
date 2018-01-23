package io.zeebe.gossip.failuredetection;

import java.util.List;

import io.zeebe.transport.ClientRequest;

public final class RequestCloser
{

    private RequestCloser()
    {
    }

    public static void close(List<ClientRequest> requests)
    {
        if (requests != null && !requests.isEmpty())
        {
            for (ClientRequest request : requests)
            {
                request.close();
            }
            requests.clear();
        }
    }
}
