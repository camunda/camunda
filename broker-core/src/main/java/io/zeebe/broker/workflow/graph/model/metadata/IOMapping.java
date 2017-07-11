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
package io.zeebe.broker.workflow.graph.model.metadata;

import io.zeebe.msgpack.mapping.Mapping;
/**
 * Represents an IO mapping structure. The IO mapping can consist
 * of a list of different input and output mapping's for a flow element.
 * Each input and output mapping has a source and target. The source and target
 * are represented via a json path expression.
 */
public class IOMapping
{
    private Mapping inputMappings[];
    private Mapping outputMappings[];

    public Mapping[] getInputMappings()
    {
        return this.inputMappings;
    }

    public void setInputMappings(Mapping[] inputMappings)
    {
        this.inputMappings = inputMappings;
    }

    public Mapping[] getOutputMappings()
    {
        return this.outputMappings;
    }

    public void setOutputMappings(Mapping[] outputMappings)
    {
        this.outputMappings = outputMappings;
    }
}
