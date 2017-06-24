package io.zeebe.transport;

import static org.assertj.core.api.Assertions.fail;

import org.junit.Ignore;
import org.junit.Test;

@Ignore("https://github.com/camunda-tngp/zb-transport/issues/25")
public class Issue25Test
{

    @Test
    public void failThis()
    {
        fail("implement these test cases");
        /*
         * - writing of transport message, request, response to send buffer and the error cases (e.g. when buffer is saturated)
         * - server-channels should always receive a new stream id
         * - error cases:
         *   - send buffer is saturated
         *   - request pool is exhausted
         *   - channel closes while requests are open
         *   - buffer writer throws exception
         */
    }
}
