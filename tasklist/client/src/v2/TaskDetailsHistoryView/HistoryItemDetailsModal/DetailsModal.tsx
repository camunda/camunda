/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  ComposedModal,
  ModalBody,
  ModalHeader,
  StructuredListBody,
  StructuredListCell,
  StructuredListRow,
  StructuredListWrapper,
} from '@carbon/react';
import {EventSchedule, UserAvatar} from '@carbon/react/icons';
import {useTranslation} from 'react-i18next';
import type {QueryUserTaskAuditLogsResponseBody} from '@camunda/camunda-api-zod-schemas/8.9';
import {formatDate} from 'common/dates/formatDate';
import {getOperationTypeTranslationKey} from '../getOperationTypeTranslationKey';
import styles from './styles.module.scss';

type AuditLogItem = QueryUserTaskAuditLogsResponseBody['items'][number];

type Props = {
  onClose: () => void;
  auditLog: AuditLogItem;
};

const DetailsModal: React.FC<Props> = ({onClose, auditLog}) => {
  const {t} = useTranslation();
  const {operationType, actorId, timestamp} = auditLog;

  return (
    <ComposedModal size="md" open onClose={onClose}>
      <ModalHeader title={t(getOperationTypeTranslationKey(operationType))} />
      <ModalBody>
        <StructuredListWrapper isCondensed isFlush>
          <StructuredListBody>
            <StructuredListRow className={styles.verticallyAlignedRow}>
              <StructuredListCell className={styles.firstColumn}>
                <div className={styles.iconText}>
                  <UserAvatar />
                  {t('taskDetailsHistoryModalActor')}
                </div>
              </StructuredListCell>
              <StructuredListCell>{actorId}</StructuredListCell>
            </StructuredListRow>
            <StructuredListRow className={styles.verticallyAlignedRow}>
              <StructuredListCell noWrap className={styles.firstColumn}>
                <div className={styles.iconText}>
                  <EventSchedule />
                  {t('taskDetailsHistoryModalTime')}
                </div>
              </StructuredListCell>
              <StructuredListCell>{formatDate(timestamp)}</StructuredListCell>
            </StructuredListRow>
            {auditLog.operationType === 'ASSIGN' ? (
              <>
                <StructuredListRow>
                  <h5 className={styles.titleListCell}>
                    {t('taskDetailsHistoryModalProperties')}:
                  </h5>
                </StructuredListRow>
                <StructuredListRow className={styles.verticallyAlignedRow}>
                  <StructuredListCell noWrap className={styles.firstColumn}>
                    <div className={styles.iconText}>
                      <EventSchedule />
                      {t('taskDetailsHistoryModalAssignee')}
                    </div>
                  </StructuredListCell>
                  <StructuredListCell>
                    {auditLog.relatedEntityKey}
                  </StructuredListCell>
                </StructuredListRow>
              </>
            ) : undefined}
          </StructuredListBody>
        </StructuredListWrapper>
      </ModalBody>
    </ComposedModal>
  );
};

export {DetailsModal};
