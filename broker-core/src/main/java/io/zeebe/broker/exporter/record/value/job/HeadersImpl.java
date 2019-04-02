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
package io.zeebe.broker.exporter.record.value.job;

import io.zeebe.exporter.api.record.value.job.Headers;
import java.util.Objects;

public class HeadersImpl implements Headers {
  private final String bpmnProcessId;
  private final String elementId;
  private final long elementInstanceKey;
  private final long workflowInstanceKey;
  private final long workflowKey;
  private final int workflowDefinitionVersion;

  public HeadersImpl(
      final String bpmnProcessId,
      final String elementId,
      final long elementInstanceKey,
      final long workflowInstanceKey,
      final long workflowKey,
      final int workflowDefinitionVersion) {
    this.bpmnProcessId = bpmnProcessId;
    this.elementId = elementId;
    this.elementInstanceKey = elementInstanceKey;
    this.workflowInstanceKey = workflowInstanceKey;
    this.workflowKey = workflowKey;
    this.workflowDefinitionVersion = workflowDefinitionVersion;
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  @Override
  public String getElementId() {
    return elementId;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  @Override
  public long getWorkflowInstanceKey() {
    return workflowInstanceKey;
  }

  @Override
  public long getWorkflowKey() {
    return workflowKey;
  }

  @Override
  public int getWorkflowDefinitionVersion() {
    return workflowDefinitionVersion;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final HeadersImpl headers = (HeadersImpl) o;
    return elementInstanceKey == headers.elementInstanceKey
        && workflowInstanceKey == headers.workflowInstanceKey
        && workflowKey == headers.workflowKey
        && workflowDefinitionVersion == headers.workflowDefinitionVersion
        && Objects.equals(bpmnProcessId, headers.bpmnProcessId)
        && Objects.equals(elementId, headers.elementId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        bpmnProcessId,
        elementId,
        elementInstanceKey,
        workflowInstanceKey,
        workflowKey,
        workflowDefinitionVersion);
  }

  @Override
  public String toString() {
    return "HeadersImpl{"
        + "bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", activityId='"
        + elementId
        + '\''
        + ", elementInstanceKey="
        + elementInstanceKey
        + ", workflowInstanceKey="
        + workflowInstanceKey
        + ", workflowKey="
        + workflowKey
        + ", workflowDefinitionVersion="
        + workflowDefinitionVersion
        + '}';
  }
}
