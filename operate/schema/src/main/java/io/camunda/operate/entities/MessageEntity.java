/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.entities;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;

import java.time.OffsetDateTime;
import java.util.Objects;

public class MessageEntity extends OperateZeebeEntity<MessageEntity> {

  private String messageName;
  private String correlationKey;
  private OffsetDateTime publishDate;
  private OffsetDateTime expireDate;
  private OffsetDateTime deadline;
  private Long timeToLive;
  private String messageId;
  private String variables;
  private String tenantId = DEFAULT_TENANT_ID;

  public String getMessageName() {
    return messageName;
  }

  public MessageEntity setMessageName(String messageName) {
    this.messageName = messageName;
    return this;
  }

  public String getCorrelationKey() {
    return correlationKey;
  }

  public MessageEntity setCorrelationKey(String correlationKey) {
    this.correlationKey = correlationKey;
    return this;
  }

  public OffsetDateTime getPublishDate() {
    return publishDate;
  }

  public MessageEntity setPublishDate(OffsetDateTime publishDate) {
    this.publishDate = publishDate;
    return this;
  }

  public OffsetDateTime getExpireDate() {
    return expireDate;
  }

  public MessageEntity setExpireDate(OffsetDateTime expireDate) {
    this.expireDate = expireDate;
    return this;
  }

  public OffsetDateTime getDeadline() {
    return deadline;
  }

  public MessageEntity setDeadline(OffsetDateTime deadline) {
    this.deadline = deadline;
    return this;
  }

  public Long getTimeToLive() {
    return timeToLive;
  }

  public MessageEntity setTimeToLive(Long timeToLive) {
    this.timeToLive = timeToLive;
    return this;
  }

  public String getMessageId() {
    return messageId;
  }

  public MessageEntity setMessageId(String messageId) {
    this.messageId = messageId;
    return this;
  }

  public String getVariables() {
    return variables;
  }

  public MessageEntity setVariables(String variables) {
    this.variables = variables;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public MessageEntity setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final MessageEntity that = (MessageEntity) o;
    return Objects.equals(messageName, that.messageName)
        && Objects.equals(correlationKey, that.correlationKey)
        && Objects.equals(publishDate, that.publishDate)
        && Objects.equals(expireDate, that.expireDate)
        && Objects.equals(deadline, that.deadline)
        && Objects.equals(timeToLive, that.timeToLive)
        && Objects.equals(messageId, that.messageId)
        && Objects.equals(variables, that.variables)
        && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        messageName,
        correlationKey,
        publishDate,
        expireDate,
        deadline,
        timeToLive,
        messageId,
        variables,
        tenantId);
  }
}
