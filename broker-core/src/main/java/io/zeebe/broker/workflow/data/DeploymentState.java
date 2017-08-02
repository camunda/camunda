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
package io.zeebe.broker.workflow.data;

public enum DeploymentState
{
    CREATE_DEPLOYMENT(0),
    DEPLOYMENT_CREATED(1),
    DEPLOYMENT_REJECTED(2);

    // don't change the ids because the stream processor use them for the map
    private final int id;

    DeploymentState(int id)
    {
        this.id = id;
    }

    public int id()
    {
        return id;
    }
}
