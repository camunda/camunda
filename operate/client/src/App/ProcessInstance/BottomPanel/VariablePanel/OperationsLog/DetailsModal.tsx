/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {lazy, Suspense, useState, useEffect} from 'react';
import {
  ComposedModal,
  ModalHeader,
  ModalBody,
  ModalFooter,
  Button,
  TextArea,
  Stack,
  Tag,
  StructuredListWrapper,
  StructuredListCell,
  StructuredListBody,
  Link,
  ActionableNotification,
  Layer,
} from '@carbon/react';
import {Edit, Add} from '@carbon/react/icons';
import {DataTable} from 'modules/components/DataTable';
import {formatDate} from 'modules/utils/date';
import {useUpsertNote} from 'modules/mutations/auditLog/useUpsertNote';
import type {MockAuditLogEntry} from './mocks';
import {
  Subtitle,
  FirstColumn,
  VerticallyAlignedRow,
  Title,
} from './styled';
import {beautifyJSON} from 'modules/utils/editor/beautifyJSON';
import {CheckmarkOutline} from '@carbon/react/icons';
import {
  EventSchedule,
  UserAdmin,
  UserAvatar,
  Group,
  Launch,
  Result,
  Calendar,
} from '@carbon/icons-react';
import {instanceHistoryModificationStore} from 'modules/stores/instanceHistoryModification';

const JSONEditor = lazy(async () => {
  const [{loadMonaco}, {JSONEditor}] = await Promise.all([
    import('modules/loadMonaco'),
    import('modules/components/JSONEditor'),
  ]);

  loadMonaco();

  return {default: JSONEditor};
});

const renderUserTaskDetails = (
  entry: MockAuditLogEntry,
  onClose: () => void,
): React.ReactNode | null => {
  if (!entry.details?.userTask) {
    return null;
  }

  const {assignee, candidateGroups, elementId, name, dueDate} =
    entry.details.userTask;

  if (!assignee && !candidateGroups && !elementId) {
    return null;
  }

  return (
    <div>
      <Title>Details</Title>
      <StructuredListWrapper isCondensed isFlush>
        <StructuredListBody>
          {name && (
            <VerticallyAlignedRow head>
              <FirstColumn noWrap>
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 'var(--cds-spacing-03)',
                  }}
                >
                  <Result />
                  User task
                </div>
              </FirstColumn>
              <StructuredListCell>
                <div
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 'var(--cds-spacing-02)',
                    cursor: 'pointer',
                  }}
                  onClick={() => {
                    if (elementId) {
                      instanceHistoryModificationStore.addExpandedFlowNodeInstanceIds(
                        elementId,
                      );
                    }
                    onClose();
                  }}
                >
                  <span>{name}</span>
                  <Launch size={14} />
                </div>
              </StructuredListCell>
            </VerticallyAlignedRow>
          )}
          {assignee && (
            <VerticallyAlignedRow>
              <FirstColumn noWrap>
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 'var(--cds-spacing-03)',
                  }}
                >
                  <UserAdmin />
                  Assignee
                </div>
              </FirstColumn>
              <StructuredListCell>{assignee}</StructuredListCell>
            </VerticallyAlignedRow>
          )}
          {candidateGroups && (
            <VerticallyAlignedRow>
              <FirstColumn noWrap>
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 'var(--cds-spacing-03)',
                  }}
                >
                  <Group />
                  Candidate groups
                </div>
              </FirstColumn>
              <StructuredListCell>{candidateGroups}</StructuredListCell>
            </VerticallyAlignedRow>
          )}
          {dueDate && (
            <VerticallyAlignedRow>
              <FirstColumn noWrap>
              <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 'var(--cds-spacing-03)',
                  }}
                >
                  <Calendar />
                  Due date
                </div>
              </FirstColumn>
              <StructuredListCell>{formatDate(dueDate, '--', 'PPP')}</StructuredListCell>
            </VerticallyAlignedRow>
          )}
        </StructuredListBody>
      </StructuredListWrapper>
    </div>
  );
};

type Props = {
  open: boolean;
  onClose: () => void;
  entry: MockAuditLogEntry | null;
};

const DetailsModal: React.FC<Props> = ({open, onClose, entry}) => {
  const [isNoteEditing, setIsNoteEditing] = useState(false);
  const [comment, setComment] = useState(entry?.comment || '');
  const {mutate: upsertNote} = useUpsertNote();
  const [isMappingsCollapsed, setIsMappingsCollapsed] = useState(false);

  useEffect(() => {
    if (entry) {
      setComment(entry.comment || '');
    }
    setIsNoteEditing(false);
  }, [entry]);

  if (!entry) {
    return null;
  }

  const handleSaveNote = () => {
    upsertNote(
      {
        id: entry.id,
        comment: comment,
      },
      {
        onSuccess: () => {
          setIsNoteEditing(false);
        },
      },
    );
  };

  const handleCancelEdit = () => {
    setComment(entry?.comment || '');
    setIsNoteEditing(false);
  };

  const formatOperationType = (type: string) => {
    return type
      .split('_')
      .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
      .join(' ');
  };

  const formatOperationState = (state: string) => {
    return state
      .split('_')
      .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
      .join(' ');
  };

  const getOperationStateType = (
    state: string,
  ): 'green' | 'red' => {
    switch (state) {
      case 'COMPLETED':
        return 'green';
      case 'FAILED':
        return 'red';
      default:
        return 'green';
    }
  };

  const renderDetails = () => {
    let detailsContent: React.ReactNode = null;

    switch (entry.operationType) {
      case 'MODIFY_PROCESS_INSTANCE': {
        const modifications = entry.details?.modifications;
        const activateInstructions = modifications?.activateInstructions ?? [];
        const terminateInstructions =
          modifications?.terminateInstructions ?? [];

        const isMoveModification =
          activateInstructions.length === 1 &&
          terminateInstructions.length === 1;

        const elementModifications: {
          id: string;
          operation: string;
          targetElement: string;
          targetElementId: string;
          previousElement: string;
        }[] = [];

        if (isMoveModification) {
          const source = terminateInstructions[0]!;
          const target = activateInstructions[0]!;
          elementModifications.push({
            id: 'move-0',
            operation: 'Move Element Instance',
            targetElement: target.elementName || target.elementId,
            targetElementId: target.elementId,
            previousElement:
              source.elementName || source.elementInstanceKey,
          });
        } else {
          activateInstructions.forEach((instr, index) => {
            elementModifications.push({
              id: `add-${index}`,
              operation: 'Add Element Instance',
              targetElement: instr.elementName || instr.elementId,
              targetElementId: instr.elementId,
              previousElement: '–',
            });
          });
          terminateInstructions.forEach((instr, index) => {
            elementModifications.push({
              id: `cancel-${index}`,
              operation: 'Cancel Element Instance',
              targetElement: instr.elementName || instr.elementInstanceKey,
              targetElementId: instr.elementInstanceKey,
              previousElement: '–',
            });
          });
        }

        const variableModifications =
          modifications?.activateInstructions?.reduce(
            (
              acc: {
                scopeId: string;
                scopeName: string;
                name: string;
                value: any;
              }[],
              instruction,
            ) => {
              instruction.variableInstructions?.forEach((varInstruction) => {
                Object.entries(varInstruction.variables).forEach(
                  ([name, value]) => {
                    acc.push({
                      scopeId: varInstruction.scopeId,
                      scopeName:
                        instruction.elementName || varInstruction.scopeId,
                      name,
                      value,
                    });
                  },
                );
              });
              return acc;
            },
            [],
          ) ?? [];

        if (
          elementModifications.length > 0 ||
          variableModifications.length > 0
        ) {
          detailsContent = (
            <Stack gap={6}>
              {elementModifications.length > 0 && (
                <Stack orientation="vertical" gap={2}>
                  <Subtitle>Element Modifications</Subtitle>
                  <DataTable
                    headers={[
                      {header: 'Operation', key: 'operation', width: '40%'},
                      {header: 'Element', key: 'element', width: '60%'},
                    ]}
                    rows={elementModifications.map((mod) => ({
                      id: mod.id,
                      operation: mod.operation,
                      element:
                        mod.operation === 'Move Element Instance' ? (
                          <div
                            style={{
                              display: 'flex',
                              alignItems: 'center',
                            }}
                          >
                            <span>From "</span>
                            <span style={{fontWeight: 600}}>{mod.previousElement}</span>
                            <span>" to "</span>
                            <div
                              style={{
                                display: 'inline-flex',
                                alignItems: 'center',
                                cursor: 'pointer',
                              }}
                              onClick={() => {
                                instanceHistoryModificationStore.addExpandedFlowNodeInstanceIds(
                                  mod.targetElementId,
                                );
                                onClose();
                              }}
                            >
                              <span style={{fontWeight: 600}}>{mod.targetElement}</span>
                              <span>"</span>
                              <Launch size={14} />
                            </div>
                          </div>
                        ) : (
                          <div
                            style={{
                              display: 'flex',
                              alignItems: 'center',
                              gap: 'var(--cds-spacing-02)',
                              cursor: 'pointer',
                            }}
                            onClick={() => {
                              instanceHistoryModificationStore.addExpandedFlowNodeInstanceIds(
                                mod.targetElementId,
                              );
                              onClose();
                            }}
                          >
                            <span>{mod.targetElement}</span>
                            <Launch size={14} />
                          </div>
                        ),
                    }))}
                  />
                </Stack>
              )}
              {variableModifications.length > 0 && (
                <Stack orientation="vertical" gap={2}>
                  <Subtitle>Variable Modifications</Subtitle>
                  <DataTable
                    isExpandable
                    expandedContents={variableModifications.reduce(
                      (acc, mod, index) => ({
                        ...acc,
                        [index.toString()]: (
                          <Suspense>
                            <JSONEditor
                              value={beautifyJSON(JSON.stringify(mod.value))}
                              readOnly
                              height="10vh"
                              width="95%"
                            />
                          </Suspense>
                        ),
                      }),
                      {},
                    )}
                    headers={[
                      {header: 'Variable name', key: 'name'},
                      {header: 'New value', key: 'value'},
                      {header: 'Element scope', key: 'scope'},
                    ]}
                    rows={variableModifications.map((mod, index) => ({
                      id: index.toString(),
                      name: mod.name,
                      value: JSON.stringify(mod.value),
                      scope: (
                        <div
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: 'var(--cds-spacing-02)',
                            cursor: 'pointer',
                          }}
                          onClick={() => {
                            instanceHistoryModificationStore.addExpandedFlowNodeInstanceIds(
                              mod.scopeId,
                            );
                            onClose();
                          }}
                        >
                          {mod.scopeName}
                          <Launch size={14} />
                        </div>
                      ),
                    }))}
                  />
                </Stack>
              )}
            </Stack>
          );
        }
        break;
      }
      case 'MIGRATE_PROCESS_INSTANCE':
        if (entry.details?.migrationPlan) {
          const {
            targetProcessDefinitionKey,
            targetProcessDefinitionName,
            mappingInstructions,
          } = entry.details.migrationPlan;

          const sourceProcessName = entry.processDefinitionName;
          const sourceProcessVersion = entry.processDefinitionVersion;
          const targetNameRaw = targetProcessDefinitionName ?? targetProcessDefinitionKey;
          const versionFromName = (name: string | undefined) => {
            if (!name) return undefined;
            const match = name.match(/\bv\s*(\d+)\s*$/i);
            return match ? match[1] : undefined;
          };
          const stripVersionSuffix = (name: string) =>
            name.replace(/\s*v\s*\d+\s*$/i, '').trim();

          const targetVersionFromName =
            typeof targetProcessDefinitionName === 'string'
              ? versionFromName(targetProcessDefinitionName)
              : undefined;
          const targetDisplayName =
            typeof targetProcessDefinitionName === 'string'
              ? stripVersionSuffix(targetProcessDefinitionName)
              : targetNameRaw;

          const totalMappings = mappingInstructions.length;

          detailsContent = (
            <Stack gap={6}>
              <Stack orientation="vertical" gap={2}>
                <Subtitle>Migrated Process Definition</Subtitle>
                <DataTable
                  headers={[
                    {header: 'Source', key: 'from', width: '40%'},
                    {header: 'Target', key: 'to', width: '60%'},
                  ]}
                  rows={([
                    {
                      id: 'process-def-mapping',
                      from: (
                        <div style={{display: 'flex', alignItems: 'center', gap: 'var(--cds-spacing-02)'}}>
                          <span>{sourceProcessName}</span>
                          {sourceProcessVersion !== undefined && (
                            <Tag type="gray" size="sm">{`v${sourceProcessVersion}`}</Tag>
                          )}
                        </div>
                      ),
                      to: (
                        <div style={{display: 'flex', alignItems: 'center', gap: 'var(--cds-spacing-02)'}}>
                          <span>{targetDisplayName}</span>
                          {targetVersionFromName !== undefined && (
                            <Tag type="gray" size="sm">{`v${targetVersionFromName}`}</Tag>
                          )}
                        </div>
                      ),
                    },
                  ] as any)}
                />
              </Stack>

              {mappingInstructions.length > 0 && (
                <Stack orientation="vertical" gap={2}>
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                    }}
                  >
                    <Subtitle>Element Instance Mappings</Subtitle>
                    <Button
                      kind="ghost"
                      size="sm"
                      onClick={() => setIsMappingsCollapsed((prev) => !prev)}
                    >
                      {isMappingsCollapsed
                        ? `Show all (${totalMappings})`
                        : 'Hide'}
                    </Button>
                  </div>
                  {!isMappingsCollapsed && (
                    <DataTable
                      headers={[
                        {header: 'Source', key: 'initial', width: '40%'},
                        {header: 'Target', key: 'target', width: '60%'},
                      ]}
                      rows={mappingInstructions.map((instr) => ({
                        id: `${instr.sourceElementId}-${instr.targetElementId}`,
                        initial: instr.sourceElementName ?? instr.sourceElementId,
                        target: (
                          <div
                            style={{
                              display: 'flex',
                              alignItems: 'center',
                              gap: 'var(--cds-spacing-02)',
                              cursor: 'pointer',
                            }}
                            onClick={() => {
                              instanceHistoryModificationStore.addExpandedFlowNodeInstanceIds(
                                instr.targetElementId,
                              );
                              onClose();
                            }}
                          >
                            {instr.targetElementName ?? instr.targetElementId}
                            <Launch size={14} />
                          </div>
                        ),
                      }))}
                    />
                  )}
                </Stack>
              )}
            </Stack>
          );
        }
        break;
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
                scope: (
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 'var(--cds-spacing-02)',
                      cursor: 'pointer',
                    }}
                    onClick={() => {
                      if (variable.scope) {
                        instanceHistoryModificationStore.addExpandedFlowNodeInstanceIds(
                          variable.scope.id,
                        );
                      }
                      onClose();
                    }}
                  >
                    {variable.scope?.name ?? ''}
                    <Launch size={14} />
                  </div>
                ),
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

  return (
    <ComposedModal size="md" open={open} onClose={onClose}>
      <ModalHeader
        title={formatOperationType(entry.operationType)}
        label="Operation Details"
        closeModal={onClose}
      />
      <ModalBody>
        <Stack gap={5}>
          {entry.isMultiInstanceOperation && (
            <Stack gap={2}>
              <ActionableNotification
                kind="info"
                lowContrast
                inline
                hideCloseButton
                title="This operation is part of a batch"
                actionButtonLabel="View batch operation details"
              />
            </Stack>
          )}
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
                    <Tag
                      type={getOperationStateType(entry.operationState)}
                      size="md"
                    >
                      {formatOperationState(entry.operationState)}
                    </Tag>
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
                      Start time
                    </div>
                  </FirstColumn>
                  <StructuredListCell>
                    {formatDate(entry.startTimestamp)}
                  </StructuredListCell>
                </VerticallyAlignedRow>
              </StructuredListBody>
            </StructuredListWrapper>
          </Stack>
          {entry.operationState === 'FAILED' && entry.errorMessage && (
            <>
            <Stack gap={1}>
              <Subtitle>Error Message</Subtitle>
              <div>
                {entry.errorMessage}
              </div>
            </Stack>
            </>
          )}
          {renderDetails()}
          {renderUserTaskDetails(entry, onClose)}
          <Stack gap={1}>
            <Title>Note</Title>
            {isNoteEditing ? (
              <Stack orientation="vertical" gap={5}>
                <TextArea
                  light
                  labelText=""
                  placeholder='Enter your note here...'
                  value={comment}
                  onChange={(e) => setComment(e.target.value)}
                  rows={3}
                />
                <div style={{display: 'flex', gap: 'var(--cds-spacing-03)'}}>
                  <Button size="sm" onClick={handleSaveNote}>
                    Save
                  </Button>
                  <Button
                    size="sm"
                    kind="secondary"
                    onClick={handleCancelEdit}
                  >
                    Cancel
                  </Button>
                </div>
              </Stack>
            ) : (
              <div
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 'var(--cds-spacing-03)',
                }}
              >
                {comment && (
                  <TextArea
                    light
                    labelText=""
                    value={comment}
                    readOnly
                    rows={3}
                  />
                )}
                <Button
                  kind="tertiary"
                  size="sm"
                  renderIcon={comment ? Edit : Add}
                  onClick={() => setIsNoteEditing(true)}
                >
                  {comment ? 'Edit Note' : 'Add Note'}
                </Button>
              </div>
            )}
          </Stack>
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
