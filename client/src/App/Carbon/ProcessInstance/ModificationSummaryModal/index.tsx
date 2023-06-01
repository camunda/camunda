/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useLayoutEffect, useRef} from 'react';
import {observer} from 'mobx-react';
import {
  TruncatedValueContainer,
  TruncatedValue,
  Title,
  EmptyMessage,
  DataTable,
} from './styled';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {getProcessName} from 'modules/utils/instance';
import {modificationsStore} from 'modules/stores/modifications';
import {processInstanceDetailsStatisticsStore} from 'modules/stores/processInstanceDetailsStatistics';
import {tracking} from 'modules/tracking';
import {Button, Modal} from '@carbon/react';
import {StateProps} from 'modules/components/Carbon/ModalStateManager';
import {useNotifications} from 'modules/notifications';

const OPERATION_DISPLAY_NAME = {
  ADD_TOKEN: 'Add',
  CANCEL_TOKEN: 'Cancel',
  MOVE_TOKEN: 'Move',
  ADD_VARIABLE: 'Add',
  EDIT_VARIABLE: 'Edit',
};

const ModificationSummaryModal: React.FC<StateProps> = observer(
  ({open, setOpen}) => {
    const notifications = useNotifications();

    const flowNodeModificationsTableRef = useRef<HTMLDivElement>(null);
    const variableModificationsTableRef = useRef<HTMLDivElement>(null);

    const {processInstance} = processInstanceDetailsStore.state;

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

    const processInstanceId = processInstance.id;
    const hasParentProcess = processInstance.parentInstanceId !== null;

    const areModificationsInvalid =
      processInstanceDetailsStatisticsStore.willAllFlowNodesBeCanceled &&
      hasParentProcess;

    return (
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
        onRequestSubmit={() => {
          tracking.track({
            eventName: 'apply-modifications',
            addToken: modificationsStore.flowNodeModifications.filter(
              ({operation}) => operation === 'ADD_TOKEN'
            ).length,
            cancelToken: modificationsStore.flowNodeModifications.filter(
              ({operation}) => operation === 'CANCEL_TOKEN'
            ).length,
            moveToken: modificationsStore.flowNodeModifications.filter(
              ({operation}) => operation === 'MOVE_TOKEN'
            ).length,
            addVariable: modificationsStore.variableModifications.filter(
              ({operation}) => operation === 'ADD_VARIABLE'
            ).length,
            editVariable: modificationsStore.variableModifications.filter(
              ({operation}) => operation === 'EDIT_VARIABLE'
            ).length,
            isProcessCanceled:
              processInstanceDetailsStatisticsStore.willAllFlowNodesBeCanceled,
          });

          modificationsStore.applyModifications({
            processInstanceId,
            onSuccess: () => {
              tracking.track({eventName: 'modification-successful'});
              notifications.displayNotification('success', {
                headline: 'Modifications applied',
              });
            },
            onError: (statusCode: number) => {
              tracking.track({eventName: 'modification-failed'});
              notifications.displayNotification('error', {
                headline: 'Modification failed',
                description:
                  statusCode === 403
                    ? 'You do not have permission'
                    : 'Unable to apply modifications, please try again.',
              });
            },
          });
          setOpen(false);
        }}
      >
        <p>
          {`Planned modifications for Process Instance "${getProcessName(
            processInstance
          )} - ${processInstanceId}". Click "Apply" to proceed.`}
        </p>
        <Title>Flow Node Modifications</Title>
        {modificationsStore.flowNodeModifications.length === 0 ? (
          <EmptyMessage>No planned flow node modifications</EmptyMessage>
        ) : (
          <DataTable
            ref={flowNodeModificationsTableRef}
            columnsWithNoContentPadding={['delete']}
            headers={[
              {
                header: 'Operation',
                key: 'operation',
                width: '25%',
              },
              {
                header: 'Flow Node',
                key: 'flowNode',
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
            rows={modificationsStore.flowNodeModifications.map(
              (modification, index) => {
                const flowNodeId =
                  modification.operation === 'MOVE_TOKEN'
                    ? modification.targetFlowNode.id
                    : modification.flowNode.id;
                return {
                  id: index.toString(),
                  operation: OPERATION_DISPLAY_NAME[modification.operation],
                  flowNode: (
                    <TruncatedValueContainer>
                      {modification.operation === 'MOVE_TOKEN' ? (
                        <>
                          <TruncatedValue $hasMultipleTruncatedValue>
                            {modification.flowNode.name}
                          </TruncatedValue>
                          &nbsp;→&nbsp;
                          <TruncatedValue $hasMultipleTruncatedValue>
                            {modification.targetFlowNode.name}
                          </TruncatedValue>
                        </>
                      ) : (
                        <TruncatedValue>
                          {modification.flowNode.name}
                        </TruncatedValue>
                      )}
                    </TruncatedValueContainer>
                  ),
                  instanceKey: (
                    <TruncatedValueContainer>
                      {modification.operation !== 'ADD_TOKEN' &&
                      modification.flowNodeInstanceKey !== undefined ? (
                        <TruncatedValue>
                          {modification.flowNodeInstanceKey}
                        </TruncatedValue>
                      ) : (
                        <>--</>
                      )}
                    </TruncatedValueContainer>
                  ),
                  affectedTokens:
                    modification.operation === 'CANCEL_TOKEN'
                      ? modification.visibleAffectedTokenCount +
                        (modificationsStore.modificationsByFlowNode[flowNodeId]
                          ?.cancelledChildTokens ?? 0)
                      : modification.affectedTokenCount,
                  delete: (
                    <Button
                      kind="danger--ghost"
                      title="Delete flow node modification"
                      size="sm"
                      onClick={() => {
                        modificationsStore.removeFlowNodeModification(
                          modification
                        );
                      }}
                    >
                      Delete
                    </Button>
                  ),
                };
              }
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
              ({operation, flowNodeName, scopeId, id}) => {
                return {
                  id: `${scopeId}${id}`,
                  operation: OPERATION_DISPLAY_NAME[operation],
                  scope: <TruncatedValue>{flowNodeName}</TruncatedValue>,
                  nameValue: <div>name - value</div>,
                  emptyCell: '',
                  delete: (
                    <Button
                      kind="danger--ghost"
                      title="Delete variable modification"
                      size="sm"
                      onClick={() => {
                        modificationsStore.removeVariableModification(
                          scopeId,
                          id,
                          operation,
                          'summaryModal'
                        );
                      }}
                    >
                      Delete
                    </Button>
                  ),
                };
              }
            )}
          />
        )}
      </Modal>
    );
  }
);

export {ModificationSummaryModal};
