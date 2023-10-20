/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport.post;

import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.meta.ImportPositionEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.zeebe.ImportValueType;
import io.camunda.operate.zeebeimport.ImportPositionHolder;
import io.camunda.operate.zeebeimport.post.AdditionalData;
import io.camunda.operate.zeebeimport.post.PendingIncidentsBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static java.time.temporal.ChronoUnit.MILLIS;


@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public abstract class AbstractIncidentPostImportAction implements PostImportAction {
    private static final Logger logger = LoggerFactory.getLogger(AbstractIncidentPostImportAction.class);

    public static final long BACKOFF = 2000L;

    protected int partitionId;

    @Autowired
    @Qualifier("postImportThreadPoolScheduler")
    protected ThreadPoolTaskScheduler postImportScheduler;

    @Autowired
    protected OperateProperties operateProperties;

    @Autowired
    protected ImportPositionHolder importPositionHolder;

    protected ImportPositionEntity lastProcessedPosition;

    public AbstractIncidentPostImportAction(int partitionId) {
        this.partitionId = partitionId;
    }

    @Override
    public boolean performOneRound() throws IOException {
        List<IncidentEntity> pendingIncidents = processPendingIncidents();
        boolean smthWasProcessed = pendingIncidents.size() > 0;
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
            } catch (Exception ex) {
                logger.error(String.format("Exception occurred when performing post import for partition %d: %s. Will be retried...",
                        partitionId, ex.getMessage()), ex);
                //TODO can it fail here?
                postImportScheduler.schedule(this, Instant.now().plus(BACKOFF, MILLIS));
            }
        }
    }

    protected abstract PendingIncidentsBatch getPendingIncidents(final AdditionalData data, final Long lastProcessedPosition);
    protected abstract void searchForInstances(final List<IncidentEntity> incidents, final AdditionalData data) throws IOException;

    protected abstract boolean processIncidents(AdditionalData data, PendingIncidentsBatch batch) throws PersistenceException;

    protected List<IncidentEntity> processPendingIncidents() throws IOException {
        if (lastProcessedPosition == null) {
            lastProcessedPosition = importPositionHolder.getLatestLoadedPosition(
                    ImportValueType.INCIDENT.getAliasTemplate(), partitionId);
        }

        AdditionalData data = new AdditionalData();

        PendingIncidentsBatch batch = getPendingIncidents(data, lastProcessedPosition.getPostImporterPosition());

        if (batch.getIncidents().isEmpty()) {
            return new ArrayList<>();
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Processing pending incidents: " + batch.getIncidents());
        }

        try {

            searchForInstances(batch.getIncidents(), data);

            boolean done = processIncidents(data, batch);

            if (batch.getIncidents().size() > 0 && done) {
                lastProcessedPosition.setPostImporterPosition(batch.getLastProcessedPosition());
                importPositionHolder.recordLatestPostImportedPosition(lastProcessedPosition);
            }

            if (logger.isDebugEnabled()) {
                logger.debug("Finished processing");
            }

        } catch (IOException | PersistenceException e) {
            final String message = String.format(
                    "Exception occurred, while processing pending incidents: %s",
                    e.getMessage());
            throw new OperateRuntimeException(message, e);
        }
        return batch.getIncidents();
    }
}
