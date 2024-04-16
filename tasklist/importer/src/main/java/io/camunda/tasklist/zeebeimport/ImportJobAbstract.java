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
package io.camunda.tasklist.zeebeimport;

import static io.camunda.tasklist.util.ElasticsearchUtil.ZEEBE_INDEX_DELIMITER;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.entities.meta.ImportPositionEntity;
import io.camunda.tasklist.exceptions.NoSuchIndexException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class ImportJobAbstract implements ImportJob {

  private static final Logger LOGGER = LoggerFactory.getLogger(ImportJob.class);

  protected ImportBatch importBatch;

  protected ImportPositionEntity previousPosition;

  protected ImportPositionEntity lastProcessedPosition;

  protected OffsetDateTime creationTime;

  @Autowired protected ImportPositionHolder importPositionHolder;

  @Autowired protected RecordsReaderHolder recordsReaderHolder;

  @Autowired protected ObjectMapper objectMapper;

  @Autowired protected TasklistProperties tasklistProperties;

  @Autowired private ImportBatchProcessorFactory importBatchProcessorFactory;

  @Autowired(required = false)
  private List<ImportListener> importListeners;

  @Override
  public Boolean call() {
    processPossibleIndexChange();

    // separate importbatch in sub-batches per index
    final List<ImportBatch> subBatches = createSubBatchesPerIndexName();

    for (ImportBatch subBatch : subBatches) {
      final boolean success = processOneIndexBatch(subBatch);
      if (!success) {
        notifyImportListenersAsFailed(importBatch);
        return false;
      } // else continue
    }
    importPositionHolder.recordLatestLoadedPosition(getLastProcessedPosition());
    for (ImportBatch subBatch : subBatches) {
      notifyImportListenersAsFinished(subBatch);
    }
    return true;
  }

  private String extractZeebeVersionFromIndexName(String indexName) {
    final String[] split = indexName.split(ZEEBE_INDEX_DELIMITER);
    final String zeebeVersion;
    if (split.length >= 3) {
      zeebeVersion = split[2].replace("-snapshot", "");
    } else {
      // last version before introducing versions in index names was 0.22.0
      zeebeVersion = "0.22.0";
    }
    return zeebeVersion;
  }

  private boolean processOneIndexBatch(ImportBatch subBatch) {
    try {
      final String version = extractZeebeVersionFromIndexName(subBatch.getLastRecordIndexName());
      final ImportBatchProcessor importBatchProcessor =
          importBatchProcessorFactory.getImportBatchProcessor(version);
      importBatchProcessor.performImport(subBatch);
      return true;
    } catch (Exception ex) {
      LOGGER.error(ex.getMessage(), ex);
      return false;
    }
  }

  public void recordLatestScheduledPosition() {
    importPositionHolder.recordLatestScheduledPosition(
        importBatch.getAliasName(), importBatch.getPartitionId(), getLastProcessedPosition());
  }

  public ImportPositionEntity getLastProcessedPosition() {
    if (lastProcessedPosition == null) {
      final long lastRecordPosition = importBatch.getLastProcessedPosition(objectMapper);
      final long lastSequence = importBatch.getLastProcessedSequence(objectMapper);
      if (lastRecordPosition != 0 || lastSequence != 0) {
        lastProcessedPosition =
            ImportPositionEntity.createFrom(
                lastSequence,
                previousPosition,
                lastRecordPosition,
                importBatch.getLastRecordIndexName());
      } else {
        lastProcessedPosition = previousPosition;
      }
    }
    return lastProcessedPosition;
  }

  public boolean indexChange() {
    if (importBatch.getLastRecordIndexName() != null
        && previousPosition != null
        && previousPosition.getIndexName() != null) {
      return !importBatch.getLastRecordIndexName().equals(previousPosition.getIndexName());
    } else {
      return false;
    }
  }

  protected void notifyImportListenersAsFinished(ImportBatch importBatch) {
    if (importListeners != null) {
      for (ImportListener importListener : importListeners) {
        importListener.finished(importBatch);
      }
    }
  }

  protected void notifyImportListenersAsFailed(ImportBatch importBatch) {
    if (importListeners != null) {
      for (ImportListener importListener : importListeners) {
        importListener.failed(importBatch);
      }
    }
  }

  public OffsetDateTime getCreationTime() {
    return creationTime;
  }

  public void processPossibleIndexChange() {
    // if there was index change, comparing with previous batch, or there are more than one indices
    // in current batch, refresh Zeebe indices

    if (indexChange() || importBatch.hasMoreThanOneUniqueHitId()) {
      refreshZeebeIndices();
      // reread batch
      final RecordsReader recordsReader =
          recordsReaderHolder.getRecordsReader(
              importBatch.getPartitionId(), importBatch.getImportValueType());
      if (recordsReader == null) {
        LOGGER.warn(
            "Unable to find records reader for partitionId {} and ImportValueType {}",
            importBatch.getPartitionId(),
            importBatch.getImportValueType());
        return;
      }
      try {
        final boolean useOnlyPosition = tasklistProperties.getImporter().isUseOnlyPosition();
        final ImportBatch newImportBatch;
        if (!useOnlyPosition && previousPosition.getSequence() > 0) {
          newImportBatch =
              recordsReader.readNextBatchBySequence(
                  previousPosition.getSequence(),
                  importBatch.getLastProcessedSequence(objectMapper));

          final Long lastSequenceFromInitialBatch =
              importBatch.getLastProcessedSequence(objectMapper);
          final Long lastSequenceFromNewImportBatch =
              newImportBatch.getLastProcessedSequence(objectMapper);

          if (newImportBatch == null
              || newImportBatch.getHits() == null
              || lastSequenceFromInitialBatch > lastSequenceFromNewImportBatch) {
            final String message =
                String.format(
                    "Warning! Import batch became smaller after reread. Should not happen. Will be retried. Expected last sequence %d, actual last sequence %d.",
                    lastSequenceFromInitialBatch, lastSequenceFromNewImportBatch);
            throw new TasklistRuntimeException(message);
          }
        } else {
          newImportBatch =
              recordsReader.readNextBatchByPositionAndPartition(
                  previousPosition.getPosition(),
                  importBatch.getLastProcessedPosition(objectMapper));
          if (newImportBatch == null
              || newImportBatch.getHits() == null
              || newImportBatch.getHits().size() < importBatch.getHits().size()) {
            throw new TasklistRuntimeException(
                "Warning! Import batch became smaller after reread. Should not happen. Will be retried.");
          }
        }
        importBatch = newImportBatch;
      } catch (NoSuchIndexException ex) {
        LOGGER.warn("Indices are not found" + importBatch.toString());
      }
    }
  }
}
