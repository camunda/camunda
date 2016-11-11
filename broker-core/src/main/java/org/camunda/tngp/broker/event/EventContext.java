/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.broker.event;

import org.camunda.tngp.broker.log.LogWriter;
import org.camunda.tngp.broker.transport.worker.spi.ResourceContext;
import org.camunda.tngp.log.Log;

public class EventContext implements ResourceContext
{
    protected volatile Log[] logs  = new Log[0];

    @Override
    public int getResourceId()
    {
        return 0;
    }

    @Override
    public String getResourceName()
    {
        return null;
    }

    @Override
    public LogWriter getLogWriter()
    {
        return null;
    }

    public void setLogs(Log[] logs)
    {
        this.logs = logs;
    }

    public Log[] getLogs()
    {
        return logs;
    }

    public Log getLogById(int id)
    {
        final Log[] logsCopy = logs;

        for (int i = 0; i < logsCopy.length; i++)
        {
            final Log log = logsCopy[i];
            if (log.getId() == id)
            {
                return log;
            }
        }

        return null;
    }

}
