/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Consumer;

import org.junit.Test;

public class CreditsRequestBufferTest implements Consumer<CreditsRequest>
{

    private static final CreditsRequest CREDITS_REQUEST = new CreditsRequest();

    static
    {
        CREDITS_REQUEST.setSubscriberKey(123L);
        CREDITS_REQUEST.setCredits(456);
    }

    private int requestsProcessed = 0;

    @Test
    public void shouldHandlePaddingOnBuffer()
    {
        final CreditsRequestBuffer creditsRequestBuffer = new CreditsRequestBuffer(5, this);

        for (int i = 0; i < 10; i++)
        {
            reset();
            int requests = 0;

            while (creditsRequestBuffer.offerRequest(CREDITS_REQUEST))
            {
                requests++;
            }

            creditsRequestBuffer.handleRequests();

            assertThat(requestsProcessed).isEqualTo(requests);
        }
    }

    public void reset()
    {
        requestsProcessed = 0;
    }

    @Override
    public void accept(final CreditsRequest creditsRequest)
    {
        assertThat(creditsRequest).isEqualTo(CREDITS_REQUEST);
        requestsProcessed++;
    }
}
