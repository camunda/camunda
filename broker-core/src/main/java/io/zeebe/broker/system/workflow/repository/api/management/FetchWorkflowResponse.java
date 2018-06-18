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
package io.zeebe.broker.system.workflow.repository.api.management;

import io.zeebe.clustering.management.*;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class FetchWorkflowResponse implements BufferReader, BufferWriter {
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final FetchWorkflowResponseEncoder bodyEncoder = new FetchWorkflowResponseEncoder();

  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final FetchWorkflowResponseDecoder bodyDecoder = new FetchWorkflowResponseDecoder();

  private long workflowKey = FetchWorkflowRequestEncoder.workflowKeyNullValue();
  private int version = FetchWorkflowRequestEncoder.versionNullValue();
  private long deploymentKey = FetchWorkflowRequestEncoder.versionNullValue();
  private final DirectBuffer bpmnProcessId = new UnsafeBuffer(0, 0);
  private final DirectBuffer bpmnXml = new UnsafeBuffer(0, 0);

  @Override
  public int getLength() {
    return headerEncoder.encodedLength()
        + bodyEncoder.sbeBlockLength()
        + FetchWorkflowResponseDecoder.bpmnProcessIdHeaderLength()
        + bpmnProcessId.capacity()
        + FetchWorkflowResponseDecoder.bpmnXmlHeaderLength()
        + bpmnXml.capacity();
  }

  public FetchWorkflowResponse workflowKey(long workflowKey) {
    this.workflowKey = workflowKey;
    return this;
  }

  public FetchWorkflowResponse version(int version) {
    this.version = version;
    return this;
  }

  public FetchWorkflowResponse deploymentKey(long deploymentKey) {
    this.deploymentKey = deploymentKey;
    return this;
  }

  public FetchWorkflowResponse bpmnProcessId(DirectBuffer bpmnProcessId) {
    this.bpmnProcessId.wrap(bpmnProcessId);
    return this;
  }

  public FetchWorkflowResponse bpmnXml(DirectBuffer bpmnXml) {
    this.bpmnXml.wrap(bpmnXml);
    return this;
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    headerEncoder
        .wrap(buffer, offset)
        .blockLength(bodyEncoder.sbeBlockLength())
        .templateId(bodyEncoder.sbeTemplateId())
        .schemaId(bodyEncoder.sbeSchemaId())
        .version(bodyEncoder.sbeSchemaVersion());

    bodyEncoder
        .wrap(buffer, offset + headerEncoder.encodedLength())
        .workflowKey(workflowKey)
        .version(version)
        .deploymentKey(deploymentKey)
        .putBpmnProcessId(bpmnProcessId, 0, bpmnProcessId.capacity())
        .putBpmnXml(bpmnXml, 0, bpmnXml.capacity());
  }

  public boolean tryWrap(DirectBuffer buffer, int offset, int length) {
    headerDecoder.wrap(buffer, offset);

    return headerDecoder.schemaId() == bodyDecoder.sbeSchemaId()
        && headerDecoder.templateId() == bodyDecoder.sbeTemplateId();
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    headerDecoder.wrap(buffer, offset);

    offset += headerDecoder.encodedLength();

    bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

    workflowKey = bodyDecoder.workflowKey();
    version = bodyDecoder.version();
    deploymentKey = bodyDecoder.deploymentKey();

    offset += headerDecoder.blockLength();

    // bpmn processId

    final int bpmnProcessIdLength = bodyDecoder.bpmnProcessIdLength();
    offset += FetchWorkflowResponseDecoder.bpmnProcessIdHeaderLength();

    if (bpmnProcessIdLength > 0) {
      bpmnProcessId.wrap(buffer, offset, bpmnProcessIdLength);
    } else {
      bpmnProcessId.wrap(0, 0);
    }

    offset += bpmnProcessIdLength;
    bodyDecoder.limit(offset);

    // bpmn xml

    final int bpmnXmlLength = bodyDecoder.bpmnXmlLength();
    offset += FetchWorkflowResponseDecoder.bpmnXmlHeaderLength();

    if (bpmnXmlLength > 0) {
      bpmnXml.wrap(buffer, offset, bpmnXmlLength);
    } else {
      bpmnXml.wrap(0, 0);
    }

    offset += bpmnXmlLength;
    bodyDecoder.limit(offset);
  }

  public long getWorkflowKey() {
    return workflowKey;
  }

  public int getVersion() {
    return version;
  }

  public long getDeploymentKey() {
    return deploymentKey;
  }

  public DirectBuffer getBpmnXml() {
    return bpmnXml;
  }

  public DirectBuffer bpmnProcessId() {
    return bpmnProcessId;
  }

  public FetchWorkflowResponse reset() {
    workflowKey = FetchWorkflowRequestEncoder.workflowKeyNullValue();
    version = FetchWorkflowRequestEncoder.versionNullValue();
    deploymentKey = FetchWorkflowRequestEncoder.versionNullValue();
    bpmnProcessId.wrap(0, 0);
    bpmnXml.wrap(0, 0);

    return this;
  }
}
