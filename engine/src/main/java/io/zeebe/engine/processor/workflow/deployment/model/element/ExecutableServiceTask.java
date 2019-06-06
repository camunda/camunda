/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.processor.workflow.deployment.model.element;

import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public class ExecutableServiceTask extends ExecutableActivity {

  private DirectBuffer type;
  private int retries;
  private DirectBuffer encodedHeaders = JobRecord.NO_HEADERS;

  public ExecutableServiceTask(String id) {
    super(id);
  }

  public DirectBuffer getType() {
    return type;
  }

  public void setType(String type) {
    this.type = BufferUtil.wrapString(type);
  }

  public int getRetries() {
    return retries;
  }

  public void setRetries(int retries) {
    this.retries = retries;
  }

  public DirectBuffer getEncodedHeaders() {
    return encodedHeaders;
  }

  public void setEncodedHeaders(DirectBuffer encodedHeaders) {
    this.encodedHeaders = encodedHeaders;
  }
}
