/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {lazy, Suspense} from 'react';
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
  ActionableNotification,
} from '@carbon/react';
import {DataTable} from 'modules/components/DataTable';
import {formatDate} from 'modules/utils/date';
import type {MockAuditLogEntry} from 'modules/mocks/auditLog';
import {CheckmarkOutline, EventSchedule, UserAvatar} from '@carbon/icons-react';
import {beautifyJSON} from 'modules/utils/editor/beautifyJSON';
import {StatusIndicator} from './StatusIndicator';

const JSONEditor = lazy(async () => {
  const [{loadMonaco}, {JSONEditor}] = await Promise.all([
    import('modules/loadMonaco'),
    import('modules/components/JSONEditor'),
  ]);

  loadMonaco();

  return {default: JSONEditor};
});

// Styled components
const VerticallyAlignedRow: any = ({children, head, ...props}: any) => (
  <tr
    {...props}
    style={{
      borderBottom: head
        ? '1px solid var(--cds-border-strong)'
        : '1px solid var(--cds-border-subtle)',
    }}
  >
    {children}
  </tr>
);

const FirstColumn: any = ({children, noWrap, ...props}: any) => (
  <StructuredListCell
    {...props}
    head
    style={{
      fontWeight: 600,
      whiteSpace: noWrap ? 'nowrap' : 'normal',
      width: '180px',
    }}
  >
    {children}
  </StructuredListCell>
);

const Title: React.FC<{children: React.ReactNode}> = ({children}) => (
  <h5 style={{marginBottom: 'var(--cds-spacing-03)', fontWeight: 600}}>
    {children}
  </h5>
);

const Subtitle: React.FC<{children: React.ReactNode}> = ({children}) => (
  <h6 style={{fontWeight: 600}}>{children}</h6>
);

type Props = {
  open: boolean;
  onClose: () => void;
  entry: MockAuditLogEntry | null;
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


  const renderDetails = () => {
    let detailsContent: React.ReactNode = null;

    switch (entry.operationType) {
      case 'ADD_VARIABLE':
      case 'UPDATE_VARIABLE': {
        const variable = entry.details?.variable;
        detailsContent = variable ? (
          <DataTable
            isExpandable
            expandedContents={{
              [variable.name]: (
                <Suspense>
                  <JSONEditor
                    value={beautifyJSON(JSON.stringify(variable.newValue))}
                    readOnly
                    height="10vh"
                    width="95%"
                  />
                </Suspense>
              ),
            }}
            headers={[
              {header: 'Variable name', key: 'name'},
              {header: 'New value', key: 'newValue'},
              {header: 'Element scope', key: 'scope'},
            ]}
            rows={([
              {
                id: variable.name,
                name: variable.name,
                newValue: JSON.stringify(variable.newValue),
                scope: variable.scope?.name ?? '',
              },
            ] as any)}
          />
        ) : null;
        break;
      }
      default:
        detailsContent = null;
    }

    if (detailsContent === null) {
      return null;
    }

    return (
      <div>
        <Title>Details</Title>
        {detailsContent}
      </div>
    );
  };

  const renderProcessReference = () => {
    if (!entry.processInstanceKey) {
      return null;
    }

    return (
      <Stack gap={1}>
        <ActionableNotification
          kind="info"
          lowContrast
          inline
          hideCloseButton
          title={`The operation is applied to the ${entry.processDefinitionName || 'Process'}`}
          actionButtonLabel="View process instance"
          onActionButtonClick={() => {
            // TODO: Navigate to process instance
            console.log('Navigate to process instance', entry.processInstanceKey);
          }}
        />
      </Stack>
    );
  };

  const renderBatchReference = () => {
    if (!entry.isMultiInstanceOperation) {
      return null;
    }

    return (
      <Stack gap={1}>
        <ActionableNotification
          kind="info"
          lowContrast
          inline
          hideCloseButton
          title="The operation is applied to multiple process instances"
          actionButtonLabel="View batch operation details"
          onActionButtonClick={() => {
            // TODO: Navigate to batch operation
            console.log('Navigate to batch operation');
          }}
        />
      </Stack>
    );
  };

  return (
    <ComposedModal size="md" open={open} onClose={onClose}>
      <ModalHeader
        title={formatOperationType(entry.operationType)}
        closeModal={onClose}
      />
      <ModalBody>
        <Stack gap={6}>
          <Stack gap={1}>
            <StructuredListWrapper isCondensed isFlush>
              <StructuredListBody>
                <VerticallyAlignedRow head>
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
                      <EventSchedule />
                      Time
                    </div>
                  </FirstColumn>
                  <StructuredListCell>
                    {formatDate(entry.startTimestamp)}
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
                      Applied by
                    </div>
                  </FirstColumn>
                  <StructuredListCell>{entry.user}</StructuredListCell>
                </VerticallyAlignedRow>
              </StructuredListBody>
            </StructuredListWrapper>
          </Stack>
          {entry.operationState === 'fail' && entry.errorMessage && (
            <Stack gap={1}>
              <Subtitle>Error Message</Subtitle>
              <div>{entry.errorMessage}</div>
            </Stack>
          )}
          {renderBatchReference()}
          {renderProcessReference()}
          {renderDetails()}
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

