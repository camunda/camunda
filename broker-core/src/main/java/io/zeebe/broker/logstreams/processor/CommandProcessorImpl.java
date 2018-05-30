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
package io.zeebe.broker.logstreams.processor;

import io.zeebe.broker.logstreams.processor.CommandProcessor.CommandControl;
import io.zeebe.broker.logstreams.processor.CommandProcessor.CommandResult;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.intent.Intent;

public class CommandProcessorImpl<T extends UnpackedObject> implements TypedRecordProcessor<T>, CommandControl, CommandResult
{

    private final CommandProcessor<T> wrappedProcessor;

    private boolean isAccepted;
    private boolean respond;

    private Intent newState;

    private RejectionType rejectionType;
    private String rejectionReason;

    public CommandProcessorImpl(CommandProcessor<T> commandProcessor)
    {
        this.wrappedProcessor = commandProcessor;
    }

    @Override
    public void processRecord(TypedRecord<T> record)
    {
        wrappedProcessor.onCommand(record, this);
        respond = record.getMetadata().hasRequestMetadata();
    }

    @Override
    public boolean executeSideEffects(TypedRecord<T> record, TypedResponseWriter responseWriter)
    {
        if (respond)
        {
            if (isAccepted)
            {
                return responseWriter.writeEvent(newState, record);
            }
            else
            {
                return responseWriter.writeRejection(record, rejectionType, rejectionReason);
            }
        }
        else
        {
            return true;
        }
    }

    @Override
    public long writeRecord(TypedRecord<T> record, TypedStreamWriter writer)
    {
        if (isAccepted)
        {
            return writer.writeFollowUpEvent(record.getKey(), newState, record.getValue());
        }
        else
        {
            return writer.writeRejection(record, rejectionType, rejectionReason);
        }
    }

    @Override
    public void updateState(TypedRecord<T> record)
    {
        if (isAccepted)
        {
            wrappedProcessor.updateStateOnAccept(record);
        }
        else
        {
            wrappedProcessor.updateStateOnReject(record);
        }
    }

    @Override
    public CommandResult accept(Intent newState)
    {
        isAccepted = true;
        this.newState = newState;
        return this;
    }

    @Override
    public CommandResult reject(RejectionType type, String reason)
    {
        isAccepted = false;
        this.rejectionType = type;
        this.rejectionReason = reason;
        return this;
    }

}
