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
package io.camunda.operate.zeebeimport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.operate.entities.HitEntity;
import io.camunda.operate.zeebe.ImportValueType;
import io.camunda.operate.zeebe.ZeebeESConstants;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One batch for importing Zeebe data. Contains list of records as well as partition id and value
 * type of the records.
 */
public class ImportBatch {

  private static final Logger LOGGER = LoggerFactory.getLogger(ImportBatch.class);

  private int partitionId;

  private ImportValueType importValueType;

  private List<HitEntity> hits;

  private String lastRecordIndexName;

  private int finishedWiCount = 0;

  private OffsetDateTime scheduledTime;

  public ImportBatch(
      int partitionId,
      ImportValueType importValueType,
      List<HitEntity> hits,
      String lastRecordIndexName) {
    this.partitionId = partitionId;
    this.importValueType = importValueType;
    this.hits = hits;
    this.lastRecordIndexName = lastRecordIndexName;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public void setPartitionId(int partitionId) {
    this.partitionId = partitionId;
  }

  public ImportValueType getImportValueType() {
    return importValueType;
  }

  public void setImportValueType(ImportValueType importValueType) {
    this.importValueType = importValueType;
  }

  public List<HitEntity> getHits() {
    return hits;
  }

  public void setHits(List<HitEntity> hits) {
    this.hits = hits;
  }

  public int getRecordsCount() {
    return hits.size();
  }

  public void incrementFinishedWiCount() {
    finishedWiCount++;
  }

  public int getFinishedWiCount() {
    return finishedWiCount;
  }

  public String getLastRecordIndexName() {
    return lastRecordIndexName;
  }

  public void setLastRecordIndexName(String lastRecordIndexName) {
    this.lastRecordIndexName = lastRecordIndexName;
  }

  public Long getLastProcessedPosition(ObjectMapper objectMapper) {
    return getLastProcessed(ZeebeESConstants.POSITION_FIELD_NAME, objectMapper, 0L);
  }

  public Long getLastProcessedSequence(ObjectMapper objectMapper) {
    return getLastProcessed(ZeebeESConstants.SEQUENCE_FIELD_NAME, objectMapper, 0L);
  }

  private long getLastProcessed(
      final String fieldName, final ObjectMapper objectMapper, final Long defaultValue) {
    try {
      if (hits != null && hits.size() != 0) {
        final ObjectNode node =
            objectMapper.readValue(hits.get(hits.size() - 1).getSourceAsString(), ObjectNode.class);
        if (node.has(fieldName)) {
          return node.get(fieldName).longValue();
        }
      }
    } catch (IOException e) {
      LOGGER.warn(
          String.format(
              "Unable to parse Zeebe object for getting field %s : %s", fieldName, e.getMessage()),
          e);
    }
    return defaultValue;
  }

  public String getAliasName() {
    return importValueType.getAliasTemplate();
  }

  public OffsetDateTime getScheduledTime() {
    return scheduledTime;
  }

  public ImportBatch setScheduledTime(final OffsetDateTime scheduledTime) {
    this.scheduledTime = scheduledTime;
    return this;
  }

  @Override
  public int hashCode() {
    int result = partitionId;
    result = 31 * result + (importValueType != null ? importValueType.hashCode() : 0);
    result = 31 * result + (hits != null ? hits.hashCode() : 0);
    result = 31 * result + (lastRecordIndexName != null ? lastRecordIndexName.hashCode() : 0);
    result = 31 * result + finishedWiCount;
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final ImportBatch that = (ImportBatch) o;

    if (partitionId != that.partitionId) {
      return false;
    }
    if (finishedWiCount != that.finishedWiCount) {
      return false;
    }
    if (importValueType != that.importValueType) {
      return false;
    }
    if (!Objects.equals(hits, that.hits)) {
      return false;
    }
    return Objects.equals(lastRecordIndexName, that.lastRecordIndexName);
  }
}
