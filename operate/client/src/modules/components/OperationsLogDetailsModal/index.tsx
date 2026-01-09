/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  CodeSnippet,
  Link,
  Modal,
  StructuredListBody,
  StructuredListCell,
  StructuredListWrapper,
} from '@carbon/react';
import {formatDate} from 'modules/utils/date';
import {
  ArrowRight,
  BatchJob,
  CheckmarkOutline,
  EventSchedule,
  UserAvatar,
} from '@carbon/react/icons';
import {
  FirstColumn,
  IconText,
  ParagraphWithIcon,
  VerticallyAlignedRow,
} from './styled';
import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.9/audit-log';
import {spaceAndCapitalize} from 'modules/utils/spaceAndCapitalize';
import {OperationsLogStateIcon} from 'modules/components/OperationsLogStateIcon';
import {formatBatchTitle} from 'modules/utils/operationsLog';

type Props = {
  isOpen: boolean;
  onClose: () => void;
  auditLog: AuditLog;
};

type DetailsModalState = {
  isOpen: boolean;
  auditLog?: AuditLog;
};

const DetailsModal: React.FC<Props> = ({isOpen, onClose, auditLog}) => {
  const formatModalHeading = () => {
    return `${spaceAndCapitalize(
      auditLog.operationType.toString(),
    )} ${spaceAndCapitalize(auditLog.entityType.toString())}`;
  };

  return (
    <Modal
      size="md"
      open={isOpen}
      onRequestClose={onClose}
      modalHeading={formatModalHeading()}
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
              <IconText>
                <CheckmarkOutline />
                Status
              </IconText>
            </FirstColumn>
            <StructuredListCell>
              <IconText>
                <OperationsLogStateIcon
                  state={auditLog.result}
                  data-testid={`${auditLog.auditLogKey}-icon`}
                />
                {spaceAndCapitalize(auditLog.result.toString())}
              </IconText>
            </StructuredListCell>
          </VerticallyAlignedRow>
          <VerticallyAlignedRow>
            <FirstColumn>
              <IconText>
                <UserAvatar />
                Actor
              </IconText>
            </FirstColumn>
            <StructuredListCell>{auditLog.actorId}</StructuredListCell>
          </VerticallyAlignedRow>
          <VerticallyAlignedRow>
            <FirstColumn noWrap>
              <IconText>
                <EventSchedule />
                Time
              </IconText>
            </FirstColumn>
            <StructuredListCell>
              {formatDate(auditLog.timestamp)}
            </StructuredListCell>
          </VerticallyAlignedRow>
          {auditLog.entityType === 'BATCH' && (
            <>
              <VerticallyAlignedRow>
                <div
                  style={{
                    padding: '15px 0',
                  }}
                >
                  <h5>Applied to:</h5>
                </div>
              </VerticallyAlignedRow>
              <VerticallyAlignedRow>
                <FirstColumn noWrap>
                  <IconText>
                    <BatchJob />
                    Multiple {formatBatchTitle(auditLog.batchOperationType)}
                    <CodeSnippet type="inline">
                      {auditLog.batchOperationKey}
                    </CodeSnippet>
                  </IconText>
                </FirstColumn>
                <StructuredListCell>
                  <IconText>
                    <Link
                      href={`/batch-operations/${auditLog.batchOperationKey}`}
                      target="_self"
                    >
                      View batch operation details&nbsp;
                      <ArrowRight />
                    </Link>
                  </IconText>
                </StructuredListCell>
              </VerticallyAlignedRow>
            </>
          )}
        </StructuredListBody>
      </StructuredListWrapper>
    </Modal>
  );
};

export type {DetailsModalState};
export {DetailsModal};
