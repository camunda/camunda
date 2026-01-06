/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  Modal,
  StructuredListWrapper,
  StructuredListCell,
  StructuredListBody,
  Link,
} from '@carbon/react';
import {formatDate} from 'modules/utils/date';
import {
  CheckmarkOutline,
  BatchJob,
  EventSchedule,
  UserAvatar,
} from '@carbon/react/icons';
import {
  FirstColumn,
  OperationLogName,
  ParagraphWithIcon,
  VerticallyAlignedRow,
} from './styled';
import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.9/audit-log';
import {spaceAndCapitalize} from 'modules/utils/spaceAndCapitalize';
import {AuditLogIcon} from './AuditLogIcon';

type Props = {
  isOpen: boolean;
  onClose: () => void;
  auditLog: AuditLog;
};

const DetailsModal: React.FC<Props> = ({isOpen, onClose, auditLog}) => {
  return (
    <Modal
      size="md"
      open={isOpen}
      onRequestClose={onClose}
      modalHeading={`${spaceAndCapitalize(auditLog.operationType.toString())} ${spaceAndCapitalize(
        auditLog.entityType.toString(),
      )}`}
      primaryButtonDisabled
      secondaryButtonText="Close"
    >
      {auditLog.entityType !== 'BATCH' && auditLog.batchOperationKey ? (
        <ParagraphWithIcon>
          <BatchJob />
          This operation is part of a batch.
          <Link
            href={`/batch-operations/${auditLog.batchOperationKey}`}
            target="_self"
          >
            View batch operation details.
          </Link>
        </ParagraphWithIcon>
      ) : undefined}
      <StructuredListWrapper isCondensed isFlush>
        <StructuredListBody>
          <VerticallyAlignedRow>
            <FirstColumn noWrap>
              <OperationLogName>
                <CheckmarkOutline />
                Status
              </OperationLogName>
            </FirstColumn>
            <StructuredListCell>
              <OperationLogName>
                <AuditLogIcon
                  state={auditLog.result}
                  data-testid={`${auditLog.auditLogKey}-icon`}
                />
                {spaceAndCapitalize(auditLog.result.toString())}
              </OperationLogName>
            </StructuredListCell>
          </VerticallyAlignedRow>
          <VerticallyAlignedRow>
            <FirstColumn>
              <OperationLogName>
                <UserAvatar />
                Actor
              </OperationLogName>
            </FirstColumn>
            <StructuredListCell>{auditLog.actorId}</StructuredListCell>
          </VerticallyAlignedRow>
          <VerticallyAlignedRow>
            <FirstColumn noWrap>
              <OperationLogName>
                <EventSchedule />
                Time
              </OperationLogName>
            </FirstColumn>
            <StructuredListCell>
              {formatDate(auditLog.timestamp)}
            </StructuredListCell>
          </VerticallyAlignedRow>
        </StructuredListBody>
      </StructuredListWrapper>
    </Modal>
  );
};

export {DetailsModal};
