/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {lazy, Suspense, useLayoutEffect, useRef} from 'react';
import {observer} from 'mobx-react';
import {
  TruncatedValueContainer,
  TruncatedValue,
  Title,
  EmptyMessage,
  DataTable,
  Modal,
  EmptyCell,
} from './styled';
import {modificationsStore} from 'modules/stores/modifications';
import {tracking} from 'modules/tracking';
import {Button} from '@carbon/react';
import {type StateProps} from 'modules/components/ModalStateManager';
import {Warning} from './Messages/Warning';
import {Error} from './Messages/Error';
import {OrphanedVariablesError} from './Messages/OrphanedVariablesError';
import {VariableModification} from './VariableModification';
import {beautifyJSON} from 'modules/utils/editor/beautifyJSON';
import {notificationsStore} from 'modules/stores/notifications';
import {
  useModificationsByElement,
  useWillAllElementsBeCanceled,
} from 'modules/hooks/modifications';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {getProcessDefinitionName} from 'modules/utils/instance';

const OPERATION_DISPLAY_NAME = {
  ADD_TOKEN: 'Add',
  CANCEL_TOKEN: 'Cancel',
  MOVE_TOKEN: 'Move',
  ADD_VARIABLE: 'Add',
  EDIT_VARIABLE: 'Edit',
};

const JSONEditor = lazy(async () => {
  const [{loadMonaco}, {JSONEditor}] = await Promise.all([
    import('modules/loadMonaco'),
    import('modules/components/JSONEditor'),
  ]);

  loadMonaco();

  return {default: JSONEditor};
});

const DiffEditor = lazy(async () => {
  const [{loadMonaco}, {DiffEditor}] = await Promise.all([
    import('modules/loadMonaco'),
    import('modules/components/DiffEditor'),
  ]);

  loadMonaco();

  return {default: DiffEditor};
});

const ModificationSummaryModal: React.FC<StateProps> = observer(
  ({open, setOpen}) => {
    const willAllElementsBeCanceled = useWillAllElementsBeCanceled();
    const modificationsByElement = useModificationsByElement();
    const {data: processInstance} = useProcessInstance();
    const elementModificationsTableRef = useRef<HTMLDivElement>(null);
    const variableModificationsTableRef = useRef<HTMLDivElement>(null);

    useLayoutEffect(() => {
      if (
        elementModificationsTableRef.current === null ||
        variableModificationsTableRef.current === null
      ) {
        return;
      }

      const variableModificationsTableScrollWidth =
        variableModificationsTableRef.current.offsetWidth -
        variableModificationsTableRef.current.scrollWidth;
      const elementModificationsTableScrollWidth =
        elementModificationsTableRef.current.offsetWidth -
        elementModificationsTableRef.current.scrollWidth;

      if (
        variableModificationsTableScrollWidth !==
        elementModificationsTableScrollWidth
      ) {
        elementModificationsTableRef.current.style.paddingRight = `${variableModificationsTableScrollWidth}px`;
        variableModificationsTableRef.current.style.paddingRight = `${elementModificationsTableScrollWidth}px`;
      } else {
        elementModificationsTableRef.current.style.paddingRight = '0px';
        variableModificationsTableRef.current.style.paddingRight = '0px';
      }
    });

    if (!processInstance) {
      return null;
    }

    const processInstanceId = processInstance.processInstanceKey;
    const hasParentProcess = !!processInstance.parentProcessInstanceKey;

    const areModificationsInvalid =
      willAllElementsBeCanceled && hasParentProcess;

    const hasOrphanedVariables =
      modificationsStore.hasOrphanedVariableModifications(processInstanceId);

    return (
      <Modal
        modalHeading="Apply Modifications"
        size="lg"
        primaryButtonText="Apply"
        primaryButtonDisabled={
          areModificationsInvalid ||
          hasOrphanedVariables ||
          modificationsStore.state.modifications.length === 0
        }
        secondaryButtonText="Cancel"
        open={open}
        onRequestClose={() => setOpen(false)}
        preventCloseOnClickOutside
        onRequestSubmit={() => {
          tracking.track({
            eventName: 'apply-modifications',
            addToken: modificationsStore.elementModifications.filter(
              ({operation}) => operation === 'ADD_TOKEN',
            ).length,
            cancelToken: modificationsStore.elementModifications.filter(
              ({operation}) => operation === 'CANCEL_TOKEN',
            ).length,
            moveToken: modificationsStore.elementModifications.filter(
              ({operation}) => operation === 'MOVE_TOKEN',
            ).length,
            addVariable: modificationsStore.variableModifications.filter(
              ({operation}) => operation === 'ADD_VARIABLE',
            ).length,
            editVariable: modificationsStore.variableModifications.filter(
              ({operation}) => operation === 'EDIT_VARIABLE',
            ).length,
            isProcessCanceled: willAllElementsBeCanceled,
          });

          modificationsStore.applyModifications({
            processInstanceId,
            onSuccess: () => {
              tracking.track({eventName: 'modification-successful'});

              notificationsStore.displayNotification({
                kind: 'success',
                title: 'Modifications applied',
                isDismissable: true,
              });
            },
            onError: (statusCode: number) => {
              tracking.track({eventName: 'modification-failed'});

              notificationsStore.displayNotification({
                kind: 'error',
                title: 'Modification failed',
                subtitle:
                  statusCode === 403
                    ? 'You do not have permission'
                    : 'Unable to apply modifications, please try again.',
                isDismissable: true,
              });
            },
          });
          setOpen(false);
        }}
      >
        <p>
          {`Planned modifications for Process Instance "${getProcessDefinitionName(
            processInstance,
          )} - ${processInstanceId}". Click "Apply" to proceed.`}
        </p>

        {willAllElementsBeCanceled && !hasParentProcess && <Warning />}
        {areModificationsInvalid && <Error />}
        {hasOrphanedVariables && <OrphanedVariablesError />}

        <Title>Element Modifications</Title>
        {modificationsStore.elementModifications.length === 0 ? (
          <EmptyMessage>No planned element modifications</EmptyMessage>
        ) : (
          <DataTable
            ref={elementModificationsTableRef}
            columnsWithNoContentPadding={['delete']}
            headers={[
              {header: ' ', key: 'emptyCell'},
              {
                header: 'Operation',
                key: 'operation',
                width: '25%',
              },
              {
                header: 'Element',
                key: 'element',
                width: '25%',
              },
              {
                header: 'Instance Key',
                key: 'instanceKey',
                width: '25%',
              },
              {
                header: 'Affected Tokens',
                key: 'affectedTokens',
                width: '20%',
              },
              {
                header: ' ',
                key: 'delete',
                width: '5%',
              },
            ]}
            rows={modificationsStore.elementModifications.map(
              (modification, index) => {
                const elementId =
                  modification.operation === 'MOVE_TOKEN'
                    ? modification.targetElement.id
                    : modification.element.id;
                return {
                  id: index.toString(),
                  emptyCell: <EmptyCell />,
                  operation: OPERATION_DISPLAY_NAME[modification.operation],
                  element: (
                    <TruncatedValueContainer>
                      {modification.operation === 'MOVE_TOKEN' ? (
                        <>
                          <TruncatedValue $hasMultipleTruncatedValue>
                            {modification.element.name}
                          </TruncatedValue>
                          &nbsp;→&nbsp;
                          <TruncatedValue $hasMultipleTruncatedValue>
                            {modification.targetElement.name}
                          </TruncatedValue>
                        </>
                      ) : (
                        <TruncatedValue>
                          {modification.element.name}
                        </TruncatedValue>
                      )}
                    </TruncatedValueContainer>
                  ),
                  instanceKey: (
                    <TruncatedValueContainer>
                      {modification.operation !== 'ADD_TOKEN' &&
                      modification.elementInstanceKey !== undefined ? (
                        <TruncatedValue>
                          {modification.elementInstanceKey}
                        </TruncatedValue>
                      ) : (
                        <>--</>
                      )}
                    </TruncatedValueContainer>
                  ),
                  affectedTokens: (
                    <span data-testid="affected-token-count">
                      {modification.operation === 'CANCEL_TOKEN'
                        ? modification.visibleAffectedTokenCount +
                          (modificationsByElement[elementId]
                            ?.cancelledChildTokens ?? 0)
                        : modification.affectedTokenCount}
                    </span>
                  ),
                  delete: (
                    <Button
                      kind="danger--ghost"
                      title="Delete element modification"
                      aria-label="Delete element modification"
                      size="sm"
                      onClick={() => {
                        modificationsStore.removeElementModification(
                          modification,
                        );
                      }}
                    >
                      Delete
                    </Button>
                  ),
                };
              },
            )}
          />
        )}
        <Title>Variable Modifications</Title>
        {modificationsStore.variableModifications.length === 0 ? (
          <EmptyMessage>No planned variable modifications</EmptyMessage>
        ) : (
          <DataTable
            ref={variableModificationsTableRef}
            columnsWithNoContentPadding={['delete']}
            isExpandable
            expandableRowTitle="View variable changes by expanding rows; editing disabled in read-only editor."
            expandedContents={modificationsStore.variableModifications.reduce(
              (accumulator, {id, scopeId, operation, oldValue, newValue}) => ({
                ...accumulator,
                [`${scopeId}/${id}`]: (
                  <Suspense>
                    {operation === 'ADD_VARIABLE' ? (
                      <JSONEditor
                        value={beautifyJSON(newValue)}
                        readOnly
                        height="10vh"
                        width="95%"
                      />
                    ) : (
                      <DiffEditor
                        modifiedValue={beautifyJSON(newValue)}
                        originalValue={beautifyJSON(oldValue ?? '')}
                        height="10vh"
                        width="95%"
                      />
                    )}
                  </Suspense>
                ),
              }),
              {},
            )}
            headers={[
              {
                header: 'Operation',
                key: 'operation',
                width: '25%',
              },
              {
                header: 'Scope',
                key: 'scope',
                width: '25%',
              },
              {
                header: 'Name / Value',
                key: 'nameValue',
                width: '25%',
              },
              {
                header: ' ',
                key: 'emptyCell',
                width: '20%',
              },
              {
                header: ' ',
                key: 'delete',
                width: '5%',
              },
            ]}
            rows={modificationsStore.variableModifications.map(
              ({operation, elementName, name, newValue, scopeId, id}) => {
                return {
                  id: `${scopeId}/${id}`,
                  operation: OPERATION_DISPLAY_NAME[operation],
                  scope: <TruncatedValue>{elementName}</TruncatedValue>,
                  nameValue: (
                    <VariableModification name={name} newValue={newValue} />
                  ),
                  emptyCell: '',
                  delete: (
                    <Button
                      kind="danger--ghost"
                      title="Delete variable modification"
                      aria-label="Delete variable modification"
                      size="sm"
                      onClick={() => {
                        modificationsStore.removeVariableModification(
                          scopeId,
                          id,
                          operation,
                          'summaryModal',
                        );
                      }}
                    >
                      Delete
                    </Button>
                  ),
                };
              },
            )}
          />
        )}
      </Modal>
    );
  },
);

export {ModificationSummaryModal};
