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
package io.camunda.operate.schema.migration;

import java.time.OffsetDateTime;
import java.util.Objects;

public abstract class AbstractStep implements Step {

  private String content;
  private String description;
  private OffsetDateTime createdDate;
  private OffsetDateTime appliedDate;
  private String indexName;
  private boolean isApplied = false;
  private String version;
  private Integer order = 0;

  @Override
  public boolean isApplied() {
    return isApplied;
  }

  @Override
  public Step setApplied(final boolean isApplied) {
    this.isApplied = isApplied;
    return this;
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  @Override
  public String getContent() {
    return content;
  }

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public String getVersion() {
    return version;
  }

  @Override
  public Integer getOrder() {
    return order;
  }

  @Override
  public OffsetDateTime getCreatedDate() {
    if (createdDate == null) {
      createdDate = OffsetDateTime.now();
    }
    return createdDate;
  }

  @Override
  public Step setCreatedDate(final OffsetDateTime createDate) {
    this.createdDate = createDate;
    return this;
  }

  @Override
  public OffsetDateTime getAppliedDate() {
    return appliedDate;
  }

  @Override
  public Step setAppliedDate(final OffsetDateTime appliedDate) {
    this.appliedDate = appliedDate;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AbstractStep that = (AbstractStep) o;
    return Objects.equals(indexName, that.indexName)
        && Objects.equals(version, that.version)
        && Objects.equals(order, that.order);
  }

  @Override
  public int hashCode() {
    return Objects.hash(indexName, version, order);
  }

  @Override
  public String toString() {
    return "AbstractStep{"
        + "content='"
        + content
        + '\''
        + ", description='"
        + description
        + '\''
        + ", createdDate="
        + createdDate
        + ", appliedDate="
        + appliedDate
        + ", indexName='"
        + indexName
        + '\''
        + ", isApplied="
        + isApplied
        + ", version='"
        + version
        + '\''
        + ", order="
        + order
        + '}';
  }
}
