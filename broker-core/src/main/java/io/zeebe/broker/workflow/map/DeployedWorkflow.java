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
package io.zeebe.broker.workflow.map;

import io.zeebe.model.bpmn.instance.Workflow;

public class DeployedWorkflow
{
    private final Workflow workflow;

    private final int version;

    public DeployedWorkflow(Workflow workflow, int version)
    {
        super();
        this.workflow = workflow;
        this.version = version;
    }

    public Workflow getWorkflow()
    {
        return workflow;
    }

    public int getVersion()
    {
        return version;
    }

}
