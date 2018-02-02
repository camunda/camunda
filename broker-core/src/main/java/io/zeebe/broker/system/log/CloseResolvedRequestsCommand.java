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
package io.zeebe.broker.system.log;

import java.util.concurrent.CopyOnWriteArrayList;

import io.zeebe.transport.ClientRequest;

public class CloseResolvedRequestsCommand implements Runnable
{

    protected CopyOnWriteArrayList<ClientRequest> requests = new CopyOnWriteArrayList<>();

    @Override
    public void run()
    {
        for (int i = 0; i < requests.size(); i++)
        {
            final ClientRequest request = requests.get(i);
            if (request.isDone())
            {
                request.close();
                requests.remove(i);
                // if a request is removed the next request has the same index
                i--;
            }
        }
    }

    public void addRequest(ClientRequest request)
    {
        this.requests.add(request);
    }

    public void close()
    {
        for (ClientRequest request : requests)
        {
            request.close();
        }

        requests.clear();
    }
}
