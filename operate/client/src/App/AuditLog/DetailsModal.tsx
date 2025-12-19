/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {lazy, Suspense} from 'react';
import {useNavigate} from 'react-router-dom';
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
  CodeSnippet,
  InlineNotification,
  Link,
} from '@carbon/react';
import styled from 'styled-components';
import {DataTable} from 'modules/components/DataTable';
import {formatDate} from 'modules/utils/date';
import type {MockAuditLogEntry} from 'modules/mocks/auditLog';
import {
  CheckmarkOutline,
  EventSchedule,
  UserAvatar,
  ArrowRight,
  BatchJob,
  SoftwareResource,
  Launch,
} from '@carbon/icons-react';
import {beautifyJSON} from 'modules/utils/editor/beautifyJSON';
import {StatusIndicator} from './StatusIndicator';
import {Paths} from 'modules/Routes';

const JSONEditor = lazy(async () => {
  const [{loadMonaco}, {JSONEditor}] = await Promise.all([
    import('modules/loadMonaco'),
    import('modules/components/JSONEditor'),
  ]);

  loadMonaco();

  return {default: JSONEditor};
});

// Styled components
const StyledModalBody = styled(ModalBody)`
  .cds--layer-two.cds--modal-content.cds--modal-scroll-content--no-fade {
    padding-top: 0;
    padding-bottom: 0;
  }
`;

const StyledStructuredListWrapper = styled(StructuredListWrapper)`
  .cds--structured-list-td {
    padding-top: 4px !important;
    padding-bottom: 4px !important;
    vertical-align: middle !important;
  }
  .cds--structured-list-th {
    vertical-align: middle !important;
  }
`;

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
      fontWeight: 400,
      whiteSpace: noWrap ? 'nowrap' : 'normal',
      width: '180px',
      verticalAlign: 'middle',
    }}
  >
    {children}
  </StructuredListCell>
);

const Title: React.FC<{children: React.ReactNode}> = ({children}) => (
  <h4 style={{marginBottom: 'var(--cds-spacing-03)', fontWeight: 600}}>
    {children}
  </h4>
);

const Subtitle: React.FC<{children: React.ReactNode}> = ({children}) => (
  <h5 style={{fontWeight: 600, marginBottom: 'var(--cds-spacing-03)'}}>{children}</h5>
);

type Props = {
  open: boolean;
  onClose: () => void;
  entry: MockAuditLogEntry | null;
};

const DetailsModal: React.FC<Props> = ({open, onClose, entry}) => {
  const navigate = useNavigate();

  if (!entry) {
    return null;
  }

  const operationTypeDisplayMap: Record<string, string> = {
    CREATE_PROCESS_INSTANCE: 'Create',
    CANCEL_PROCESS_INSTANCE: 'Cancel',
    MODIFY_PROCESS_INSTANCE: 'Modify',
    MIGRATE_PROCESS_INSTANCE: 'Migrate',
    DEPLOY_RESOURCE: 'Delete',
    DELETE_RESOURCE: 'Delete',
  };

  const formatOperationType = (type: string) => {
    if (operationTypeDisplayMap[type]) {
      return operationTypeDisplayMap[type];
    }

    return type
      .split('_')
      .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
      .join(' ');
  };

  const resolveOperationEntity = (entry: MockAuditLogEntry) => {
    if (entry.operationEntity) {
      return entry.operationEntity;
    }

    if (entry.isMultiInstanceOperation) {
      return 'BATCH';
    }

    if (entry.operationType === 'EVALUATE_DECISION') {
      return 'DECISION_INSTANCE';
    }

    if (entry.operationType.includes('RESOURCE')) {
      return 'RESOURCE';
    }

    return 'PROCESS_INSTANCE';
  };

  const formatOperationEntity = (
    entity: MockAuditLogEntry['operationEntity'],
  ) => {
    if (!entity) {
      return '-';
    }

    return entity
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
        <Subtitle>Operation changes:</Subtitle>
        {detailsContent}
      </div>
    );
  };

  const renderReference = () => {
    // For batch operations
    if (entry.isMultiInstanceOperation && entry.batchOperationId) {
      return (
        <div
          style={{
            display: 'flex',
            gap: 'var(--cds-spacing-03)',
            alignItems: 'center',
          }}
        >
          <span>Multiple process instances</span>
          <CodeSnippet
            type="inline"
            title={"Batch operation key / Click to copy"}
            aria-label={"Batch operation key / Click to copy"}
            feedback="Copied to clipboard"
          >
            {entry.batchOperationId}
          </CodeSnippet>
          <Button
            kind="ghost"
            size="sm"
            hasIconOnly
            renderIcon={Launch}
            iconDescription="Open batch operation"
            onClick={() => {
              onClose();
              navigate(Paths.batchOperationDetails(entry.batchOperationId!));
            }}
          />
        </div>
      );
    }

    // For resource operations
    if (entry.details?.resourceKey) {
      const resourceKey = entry.details.resourceKey;
      const resourceType = entry.details.resourceType || 'Resource';
      const isDelete = entry.operationType === 'DELETE_RESOURCE';
      const isForm = resourceType?.toLowerCase().includes('form');
      
      return (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 'var(--cds-spacing-03)',
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 'var(--cds-spacing-03)',
              marginTop: isDelete || isForm ? 'var(--cds-spacing-02)' : '0',
              marginBottom: isDelete || isForm ? 'var(--cds-spacing-02)' : '0',
            }}
          >
            {entry.processDefinitionName && (
              <>
                <span>{entry.processDefinitionName}</span>
                <CodeSnippet
                  type="inline"
                  title={"Resource key / Click to copy"}
                  aria-label={"Resource key / Click to copy"}
                  feedback="Copied to clipboard"
                >
                  {resourceKey}
                </CodeSnippet>
                {!isDelete && !isForm && (
                  <Button
                    kind="ghost"
                    size="sm"
                    hasIconOnly
                    renderIcon={Launch}
                    iconDescription="Open process definition"
                    onClick={() => {
                      // TODO: Navigate to process definition
                      console.log(
                        'Navigate to process definition:',
                        entry.processDefinitionName,
                      );
                    }}
                  />
                )}
              </>
            )}
          </div>
        </div>
      );
    }

    // For single process operations
    if (entry.processDefinitionName) {
      return (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 'var(--cds-spacing-03)',
          }}
        >
          <div
            style={{
              display: 'flex',
              gap: 'var(--cds-spacing-03)',
              alignItems: 'center',
            }}
          >
            <span>{entry.processDefinitionName}</span>
            {entry.processInstanceKey && (
              <CodeSnippet
                type="inline"
                title={"Process instance key / Click to copy"}
                aria-label={"Process instance key / Click to copy"}
                feedback="Copied to clipboard"
              >
                {entry.processInstanceKey}
              </CodeSnippet>
            )}
            {entry.processInstanceKey && (
              <Button
                kind="ghost"
                size="sm"
                hasIconOnly
                renderIcon={Launch}
                iconDescription="Open process instance"
                onClick={() => {
                  // TODO: Navigate to process instance
                  console.log('Navigate to process:', entry.processInstanceKey);
                }}
              />
            )}
          </div>
        </div>
      );
    }

    return null;
  };

  const referenceContent = renderReference();

  return (
    <ComposedModal size="md" open={open} onClose={onClose}>
      <ModalHeader
        title={formatOperationType(entry.operationType)}
        closeModal={onClose}
      />
      <StyledModalBody>
        <Stack gap={6}>
          <Stack gap={4}>
            <StyledStructuredListWrapper isCondensed isFlush>
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
                      <SoftwareResource />
                      Entity
                    </div>
                  </FirstColumn>
                  <StructuredListCell>
                    {formatOperationEntity(resolveOperationEntity(entry))}
                  </StructuredListCell>
                </VerticallyAlignedRow>
                {referenceContent && (
                  <VerticallyAlignedRow style={{verticalAlign: 'middle'}}>
                    <FirstColumn noWrap>
                      <div
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 'var(--cds-spacing-03)',
                        }}
                      >
                        <ArrowRight />
                        Reference
                      </div>
                    </FirstColumn>
                    <StructuredListCell>{referenceContent}</StructuredListCell>
                  </VerticallyAlignedRow>
                )}
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
            </StyledStructuredListWrapper>
            {entry.operationState === 'fail' && entry.errorMessage && (
              <InlineNotification
                kind="error"
                title="Error message:"
                subtitle={entry.errorMessage}
                hideCloseButton
                lowContrast
              />
            )}
          </Stack>
          {renderDetails()}
        </Stack>
      </StyledModalBody>
    </ComposedModal>
  );
};

export {DetailsModal};

