/**
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
package io.zeebe.broker.task.processor;

import java.io.Serializable;

public class ExpirationTimeBucket implements Serializable
{
    private static final long serialVersionUID = 1L;

    protected final long eventPosition;
    protected final long expirationTime;

    public ExpirationTimeBucket(long eventPosition, long expirationTime)
    {
        this.eventPosition = eventPosition;
        this.expirationTime = expirationTime;
    }

    public long getEventPosition()
    {
        return eventPosition;
    }

    public long getExpirationTime()
    {
        return expirationTime;
    }

}