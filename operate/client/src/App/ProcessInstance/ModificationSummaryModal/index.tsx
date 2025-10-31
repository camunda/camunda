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
import {VariableModification} from './VariableModification';
import {beautifyJSON} from 'modules/utils/editor/beautifyJSON';
import {notificationsStore} from 'modules/stores/notifications';
import {
  useModificationsByFlowNode,
  useWillAllFlowNodesBeCanceled,
} from 'modules/hooks/modifications';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {getProcessDefinitionName} from 'modules/utils/instance';
import { TrashCan } from '@carbon/react/icons';

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
    const willAllFlowNodesBeCanceled = useWillAllFlowNodesBeCanceled();
    const modificationsByFlowNode = useModificationsByFlowNode();
    const {data: processInstance} = useProcessInstance();
    const flowNodeModificationsTableRef = useRef<HTMLDivElement>(null);
    const variableModificationsTableRef = useRef<HTMLDivElement>(null);

    useLayoutEffect(() => {
      if (
        flowNodeModificationsTableRef.current === null ||
        variableModificationsTableRef.current === null
      ) {
        return;
      }

      const variableModificationsTableScrollWidth =
        variableModificationsTableRef.current.offsetWidth -
        variableModificationsTableRef.current.scrollWidth;
      const flowNodeModificationsTableScrollWidth =
        flowNodeModificationsTableRef.current.offsetWidth -
        flowNodeModificationsTableRef.current.scrollWidth;

      if (
        variableModificationsTableScrollWidth !==
        flowNodeModificationsTableScrollWidth
      ) {
        flowNodeModificationsTableRef.current.style.paddingRight = `${variableModificationsTableScrollWidth}px`;
        variableModificationsTableRef.current.style.paddingRight = `${flowNodeModificationsTableScrollWidth}px`;
      } else {
        flowNodeModificationsTableRef.current.style.paddingRight = '0px';
        variableModificationsTableRef.current.style.paddingRight = '0px';
      }
    });

    if (processInstance === null) {
      return null;
    }

    const processInstanceId = processInstance?.processInstanceKey;
    const hasParentProcess = !!processInstance?.parentProcessInstanceKey;

    const areModificationsInvalid =
      willAllFlowNodesBeCanceled && hasParentProcess;

    return (
      processInstanceId && (
        <Modal
          modalHeading="Apply Modifications"
          size="lg"
          primaryButtonText="Apply"
          primaryButtonDisabled={
            areModificationsInvalid ||
            modificationsStore.state.modifications.length === 0
          }
          secondaryButtonText="Cancel"
          open={open}
          onRequestClose={() => setOpen(false)}
          preventCloseOnClickOutside
          onRequestSubmit={() => {
            tracking.track({
              eventName: 'apply-modifications',
              addToken: modificationsStore.flowNodeModifications.filter(
                ({operation}) => operation === 'ADD_TOKEN',
              ).length,
              cancelToken: modificationsStore.flowNodeModifications.filter(
                ({operation}) => operation === 'CANCEL_TOKEN',
              ).length,
              moveToken: modificationsStore.flowNodeModifications.filter(
                ({operation}) => operation === 'MOVE_TOKEN',
              ).length,
              addVariable: modificationsStore.variableModifications.filter(
                ({operation}) => operation === 'ADD_VARIABLE',
              ).length,
              editVariable: modificationsStore.variableModifications.filter(
                ({operation}) => operation === 'EDIT_VARIABLE',
              ).length,
              isProcessCanceled: willAllFlowNodesBeCanceled,
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

          {willAllFlowNodesBeCanceled && !hasParentProcess && <Warning />}
          {areModificationsInvalid && <Error />}

          <Title>Element Modifications</Title>
          {modificationsStore.flowNodeModifications.length === 0 ? (
            <EmptyMessage>No planned element modifications</EmptyMessage>
          ) : (
            <DataTable
              ref={flowNodeModificationsTableRef}
              columnsWithNoContentPadding={['delete']}
              headers={[
                {
                  header: 'Operation',
                  key: 'operation',
                },
                {
                  header: 'Target element',
                  key: 'targetElement',
                },
                {
                  header: 'Previous element',
                  key: 'previousElement',
                },
                {
                  header: 'Affected tokens',
                  key: 'affectedTokens',
                },
                {
                  header: ' ',
                  key: 'delete',
                },
              ]}
              rows={modificationsStore.flowNodeModifications.map(
                (modification, index) => {
                  const flowNodeId =
                    modification.operation === 'MOVE_TOKEN'
                      ? modification.targetFlowNode.id
                      : modification.flowNode.id;
                  return {
                    id: index.toString(),
                    operation: OPERATION_DISPLAY_NAME[modification.operation],
                    targetElement: (
                      <TruncatedValueContainer>
                        <TruncatedValue>
                          {modification.operation === 'MOVE_TOKEN'
                            ? modification.targetFlowNode.name
                            : modification.flowNode.name}
                        </TruncatedValue>
                      </TruncatedValueContainer>
                    ),
                    previousElement: (
                      <TruncatedValueContainer>
                        {modification.operation === 'MOVE_TOKEN' ? (
                          <TruncatedValue>
                            {modification.flowNode.name}
                          </TruncatedValue>
                        ) : (
                          '–'
                        )}
                      </TruncatedValueContainer>
                    ),
                    affectedTokens: (
                      <span data-testid="affected-token-count">
                        {modification.operation === 'CANCEL_TOKEN'
                          ? modification.visibleAffectedTokenCount +
                            (modificationsByFlowNode[flowNodeId]
                              ?.cancelledChildTokens ?? 0)
                          : modification.affectedTokenCount}
                      </span>
                    ),
                    delete: (
                      <Button
                        hasIconOnly
                        kind="danger--ghost"
                        iconDescription="Delete"
                        title="Delete element modification"
                        aria-label="Delete element modification"
                        size="sm"
                        renderIcon={TrashCan}
                        onClick={() => {
                          modificationsStore.removeFlowNodeModification(
                            modification,
                          );
                        }}
                      />
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
                (
                  accumulator,
                  {id, scopeId, operation, oldValue, newValue},
                ) => ({
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
                },
                {
                  header: 'Variable name',
                  key: 'variableName',
                },
                {
                  header: 'Value',
                  key: 'value',
                },
                {
                  header: 'Element scope',
                  key: 'scope',
                },
                {
                  header: ' ',
                  key: 'delete',
                },
              ]}
              rows={modificationsStore.variableModifications.map(
                ({operation, flowNodeName, name, newValue, scopeId, id}) => {
                  return {
                    id: `${scopeId}/${id}`,
                    operation: OPERATION_DISPLAY_NAME[operation],
                    variableName: <TruncatedValue>{name}</TruncatedValue>,
                    value: (
                      <TruncatedValue>
                        {JSON.stringify(newValue)}
                      </TruncatedValue>
                    ),
                    scope: <TruncatedValue>{flowNodeName}</TruncatedValue>,
                    delete: (
                      <Button
                        hasIconOnly
                        kind="danger--ghost"
                        iconDescription="Delete"
                        title="Delete variable modification"
                        aria-label="Delete variable modification"
                        size="sm"
                        renderIcon={TrashCan}
                        onClick={() => {
                          modificationsStore.removeVariableModification(
                            scopeId,
                            id,
                            operation,
                            'summaryModal',
                          );
                        }}
                      />
                    ),
                  };
                },
              )}
            />
          )}
        </Modal>
      )
    );
  },
);

export {ModificationSummaryModal};
