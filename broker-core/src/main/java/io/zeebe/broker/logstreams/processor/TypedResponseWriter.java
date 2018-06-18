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

import org.agrona.DirectBuffer;

import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.intent.Intent;

public interface TypedResponseWriter
{

    /**
     * @return true if successful
     */
    boolean writeRejection(TypedRecord<?> record, RejectionType type, String reason);

    /**
     * @return true if successful
     */
    boolean writeRejection(TypedRecord<?> record, RejectionType type, DirectBuffer reason);

    /**
     * Writes the given record as response,
     * the source record position will be set to the record position.
     *
     * @return true if successful
     */
    boolean writeRecord(Intent intent, TypedRecord<?> record);

    /*
     * <p>
     * Writes the given record *unchanged* as response. This method should be used, if the record is already commited and
     * the source position should not change.
     * </p>
     *
     * <p>The other write methods will use the record position as source record position.</p>
     *
     * @return true if successful
     */
    boolean writeRecordUnchanged(TypedRecord<?> record);
}
