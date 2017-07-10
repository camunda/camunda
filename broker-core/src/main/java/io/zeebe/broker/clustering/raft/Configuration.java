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
package io.zeebe.broker.clustering.raft;

import java.util.List;

public class Configuration
{
    private long configurationEntryPosition;
    private int configurationEntryTerm;
    private volatile List<Member> members;

    public Configuration(final long configurationEntryPosition, final int configurationEntryTerm, final List<Member> members)
    {
        this.configurationEntryPosition = configurationEntryPosition;
        this.configurationEntryTerm = configurationEntryTerm;
        this.members = members;
    }

    public long configurationEntryPosition()
    {
        return configurationEntryPosition;
    }

    public Configuration configurationEntryPosition(final long configurationEntryPosition)
    {
        this.configurationEntryPosition = configurationEntryPosition;
        return this;
    }

    public int configurationEntryTerm()
    {
        return configurationEntryTerm;
    }

    public Configuration configurationEntryTerm(final int configurationEntryTerm)
    {
        this.configurationEntryTerm = configurationEntryTerm;
        return this;
    }

    public List<Member> members()
    {
        return members;
    }

    public Configuration members(final List<Member> members)
    {
        this.members = members;
        return this;
    }
}
