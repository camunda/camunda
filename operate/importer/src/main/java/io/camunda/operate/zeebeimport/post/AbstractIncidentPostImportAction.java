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
package io.camunda.operate.zeebeimport.post;

import static java.time.temporal.ChronoUnit.MILLIS;

import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.meta.ImportPositionEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.BackoffIdleStrategy;
import io.camunda.operate.zeebe.ImportValueType;
import io.camunda.operate.zeebeimport.ImportPositionHolder;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public abstract class AbstractIncidentPostImportAction implements PostImportAction {
  public static final long BACKOFF = 2000L;
  private static final Logger LOGGER =
      LoggerFactory.getLogger(AbstractIncidentPostImportAction.class);
  protected int partitionId;

  @Autowired
  @Qualifier("postImportThreadPoolScheduler")
  protected ThreadPoolTaskScheduler postImportScheduler;

  @Autowired protected OperateProperties operateProperties;

  @Autowired protected ImportPositionHolder importPositionHolder;
  protected ImportPositionEntity lastProcessedPosition;
  private final BackoffIdleStrategy errorStrategy;

  public AbstractIncidentPostImportAction(final int partitionId) {
    this.partitionId = partitionId;
    errorStrategy = new BackoffIdleStrategy(BACKOFF, 1.2f, 10_000);
  }

  @Override
  public boolean performOneRound() throws IOException {
    final List<IncidentEntity> pendingIncidents = processPendingIncidents();
    errorStrategy.reset();
    final boolean smthWasProcessed = pendingIncidents.size() > 0;
    return smthWasProcessed;
  }

  @Override
  public void clearCache() {
    lastProcessedPosition = null;
  }

  @Override
  public void run() {
    if (operateProperties.getImporter().isPostImportEnabled()) {
      try {
        if (performOneRound()) {
          postImportScheduler.submit(this);
        } else {
          postImportScheduler.schedule(this, Instant.now().plus(BACKOFF, MILLIS));
        }
      } catch (final Exception ex) {
        LOGGER.error(
            String.format(
                "Exception occurred when performing post import for partition %d: %s. Will be retried...",
                partitionId, ex.getMessage()),
            ex);
        errorStrategy.idle();
        postImportScheduler.schedule(this, Instant.now().plus(errorStrategy.idleTime(), MILLIS));
      }
    }
  }

  protected abstract PendingIncidentsBatch getPendingIncidents(
      final AdditionalData data, final Long lastProcessedPosition);

  protected abstract void searchForInstances(
      final List<IncidentEntity> incidents, final AdditionalData data) throws IOException;

  protected abstract boolean processIncidents(AdditionalData data, PendingIncidentsBatch batch)
      throws PersistenceException;

  protected List<IncidentEntity> processPendingIncidents() throws IOException {
    if (lastProcessedPosition == null) {
      lastProcessedPosition =
          importPositionHolder.getLatestLoadedPosition(
              ImportValueType.INCIDENT.getAliasTemplate(), partitionId);
    }

    final AdditionalData data = new AdditionalData();

    final PendingIncidentsBatch batch =
        getPendingIncidents(data, lastProcessedPosition.getPostImporterPosition());

    if (batch.getIncidents().isEmpty()) {
      return new ArrayList<>();
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Processing pending incidents: " + batch.getIncidents());
    }

    try {

      searchForInstances(batch.getIncidents(), data);

      final boolean done = processIncidents(data, batch);

      if (batch.getIncidents().size() > 0 && done) {
        lastProcessedPosition.setPostImporterPosition(batch.getLastProcessedPosition());
        importPositionHolder.recordLatestPostImportedPosition(lastProcessedPosition);
      }

      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Finished processing");
      }

    } catch (final IOException | PersistenceException e) {
      final String message =
          String.format(
              "Exception occurred, while processing pending incidents: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
    return batch.getIncidents();
  }
}
