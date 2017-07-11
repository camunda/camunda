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
package io.zeebe.broker.services;

import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.status.CountersManager;

public class Counters
{
    protected final CountersManager countersManager;

    protected final AtomicBuffer countersBuffer;

    public Counters(CountersManager countersManager, AtomicBuffer countersBuffer)
    {
        this.countersManager = countersManager;
        this.countersBuffer = countersBuffer;
    }

    public CountersManager getCountersManager()
    {
        return countersManager;
    }

    public AtomicBuffer getCountersBuffer()
    {
        return countersBuffer;
    }
}
