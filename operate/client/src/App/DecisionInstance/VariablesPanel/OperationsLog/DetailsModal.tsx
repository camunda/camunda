/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  ComposedModal,
  ModalHeader,
  ModalBody,
  ModalFooter,
  Button,
  Stack,
  StructuredListWrapper,
  StructuredListCell,
  StructuredListBody,
  InlineNotification,
} from '@carbon/react';
import {formatDate} from 'modules/utils/date';
import type {MockDecisionAuditLogEntry} from './mocks';
import {CheckmarkOutline} from '@carbon/react/icons';
import {EventSchedule, UserAvatar} from '@carbon/icons-react';
import {StatusIndicator} from 'App/AuditLog/StatusIndicator';
import {
  FirstColumn,
  VerticallyAlignedRow,
} from 'App/ProcessInstance/BottomPanel/VariablePanel/OperationsLog/styled';

type Props = {
  open: boolean;
  onClose: () => void;
  entry: MockDecisionAuditLogEntry | null;
};

const DetailsModal: React.FC<Props> = ({open, onClose, entry}) => {
  if (!entry) {
    return null;
  }

  const formatOperationType = (type: string) => {
    return type
      .split('_')
      .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
      .join(' ');
  };

  return (
    <ComposedModal size="md" open={open} onClose={onClose}>
      <ModalHeader
        title={formatOperationType(entry.operationType)}
        closeModal={onClose}
      />
      <ModalBody>
        <Stack gap={5}>
          <Stack gap={1}>
            <StructuredListWrapper isCondensed isFlush>
              <StructuredListBody>
                <VerticallyAlignedRow>
                  <FirstColumn noWrap>
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 'var(--cds-spacing-03)',
                      }}
                    >
                      <CheckmarkOutline />
                      Status
                    </div>
                  </FirstColumn>
                  <StructuredListCell>
                    <StatusIndicator status={entry.operationState} />
                  </StructuredListCell>
                </VerticallyAlignedRow>
                <VerticallyAlignedRow>
                  <FirstColumn noWrap>
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 'var(--cds-spacing-03)',
                      }}
                    >
                      <UserAvatar />
                      Actor
                    </div>
                  </FirstColumn>
                  <StructuredListCell>{entry.user}</StructuredListCell>
                </VerticallyAlignedRow>
                <VerticallyAlignedRow>
                  <FirstColumn noWrap>
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 'var(--cds-spacing-03)',
                      }}
                    >
                      <EventSchedule />
                      Time
                    </div>
                  </FirstColumn>
                  <StructuredListCell>
                    {formatDate(entry.startTimestamp)}
                  </StructuredListCell>
                </VerticallyAlignedRow>
              </StructuredListBody>
            </StructuredListWrapper>
          </Stack>
          {entry.operationState === 'fail' && entry.errorMessage && (
            <InlineNotification
              kind="error"
              title="Failure reason:"
              subtitle={entry.errorMessage}
              hideCloseButton
              lowContrast
            />
          )}
        </Stack>
      </ModalBody>
      <ModalFooter>
        <Button kind="secondary" onClick={onClose}>
          Close
        </Button>
      </ModalFooter>
    </ComposedModal>
  );
};

export {DetailsModal};

