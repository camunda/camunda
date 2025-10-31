/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useMemo} from 'react';
import {observer} from 'mobx-react';
import {Tag} from '@carbon/react';
import {Information} from '@carbon/react/icons';
import {formatDate} from 'modules/utils/date';
import {DetailsModal} from './DetailsModal';
import {mockOperationLog} from './mocks';
import type {MockAuditLogEntry} from './mocks';
import {
  TimelineContainer,
  TimelineItem,
  TimelineMarker,
  TimelineContent,
  TimelineHeader,
  TimelineTitle,
  TimelineMetadata,
  TimelineBody,
  TimelineActions,
  TimelineLine,
} from './styled';

type DetailsModalState = {
  open: boolean;
  entry: MockAuditLogEntry | null;
};

const OperationsLogTimeline: React.FC = observer(() => {
  const [detailsModal, setDetailsModal] = useState<DetailsModalState>({
    open: false,
    entry: null,
  });

  const openDetailsModal = (entry: MockAuditLogEntry) => {
    setDetailsModal({open: true, entry});
  };

  const closeDetailsModal = () => {
    setDetailsModal({open: false, entry: null});
  };

  const formatOperationType = (type: string) => {
    return type
      .split('_')
      .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
      .join(' ');
  };

  const getOperationStateType = (
    state: string,
  ): 'red' | 'green' | 'blue' | 'gray' | 'purple' | 'cyan' => {
    switch (state) {
      case 'COMPLETED':
        return 'green';
      case 'FAILED':
      case 'CANCELLED':
        return 'red';
      case 'ACTIVE':
        return 'blue';
      case 'CREATED':
        return 'cyan';
      default:
        return 'gray';
    }
  };

  const formatOperationState = (state: string) => {
    return state
      .split('_')
      .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
      .join(' ');
  };

  return (
    <>
      <TimelineContainer>
        {mockOperationLog.map((entry: MockAuditLogEntry, index: number) => (
          <TimelineItem key={entry.id}>
            <TimelineMarker $state={entry.operationState} />
            {index < mockOperationLog.length - 1 && <TimelineLine />}
            <TimelineContent>
              <TimelineHeader>
                <TimelineTitle>
                  {formatOperationType(entry.operationType)}
                </TimelineTitle>
                <Tag
                  size="sm"
                  type={getOperationStateType(entry.operationState)}
                >
                  {formatOperationState(entry.operationState)}
                </Tag>
              </TimelineHeader>

              <TimelineMetadata>
                <span>{formatDate(entry.startTimestamp)}</span>
                <span>•</span>
                <span>{entry.user}</span>
              </TimelineMetadata>

              {entry.comment && <TimelineBody>{entry.comment}</TimelineBody>}

              <TimelineActions orientation="horizontal" gap={2}>
                <button
                  type="button"
                  onClick={() => openDetailsModal(entry)}
                  title="View details"
                  style={{
                    background: 'none',
                    border: 'none',
                    cursor: 'pointer',
                    padding: '4px',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '4px',
                    color: 'var(--cds-text-primary)',
                  }}
                >
                  <Information size={16} />
                  <span style={{fontSize: '0.875rem'}}>View details</span>
                </button>
              </TimelineActions>
            </TimelineContent>
          </TimelineItem>
        ))}
      </TimelineContainer>

      <DetailsModal
        open={detailsModal.open}
        onClose={closeDetailsModal}
        entry={detailsModal.entry}
      />
    </>
  );
});

export {OperationsLogTimeline};
