/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useLayoutEffect, useRef} from 'react';

import {observer} from 'mobx-react';

import {
  Container,
  Process,
  Title,
  Info,
  DataTable,
  DeleteIcon,
  TruncatedValueContainer,
  TruncatedValue,
  EmptyMessage,
} from './styled';
import {InformationModal} from 'modules/components/InformationModal';
import Modal from 'modules/components/Modal';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {getProcessName} from 'modules/utils/instance';
import {modificationsStore} from 'modules/stores/modifications';
import {ActionButton} from 'modules/components/ActionButton';
import {VariableModification} from './VariableModification';
import {useNotifications} from 'modules/notifications';
import {Warning} from './Messages/Warning';
import {processInstanceDetailsStatisticsStore} from 'modules/stores/processInstanceDetailsStatistics';
import {Error} from './Messages/Error';
import {tracking} from 'modules/tracking';

type Props = {
  isVisible: boolean;
  onClose: () => void;
};

const OPERATION_DISPLAY_NAME = {
  ADD_TOKEN: 'Add',
  CANCEL_TOKEN: 'Cancel',
  MOVE_TOKEN: 'Move',
  ADD_VARIABLE: 'Add',
  EDIT_VARIABLE: 'Edit',
};

const ModificationSummaryModal: React.FC<Props> = observer(
  ({isVisible, onClose}) => {
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
      <InformationModal
        size="CUSTOM"
        width="1104px"
        maxHeight="90%"
        isVisible={isVisible}
        onClose={onClose}
        title="Apply Modifications"
        body={
          <Container>
            <Info>
              Planned modifications for Process Instance{' '}
              {
                <Process>{`"${getProcessName(
                  processInstance
                )} - ${processInstanceId}"`}</Process>
              }
              . Click "Apply" to proceed.
            </Info>
            {processInstanceDetailsStatisticsStore.willAllFlowNodesBeCanceled &&
              !hasParentProcess && <Warning />}
            {areModificationsInvalid && <Error />}

            <Title>Flow Node Modifications</Title>
            {modificationsStore.flowNodeModifications.length === 0 ? (
              <EmptyMessage>No planned flow node modifications</EmptyMessage>
            ) : (
              <DataTable
                ref={flowNodeModificationsTableRef}
                hasFixedColumnWidths
                headerColumns={[
                  {
                    cellContent: 'Operation',
                    isBold: true,
                    width: '25%',
                  },
                  {
                    cellContent: 'Flow Node',
                    isBold: true,
                    width: '25%',
                  },
                  {
                    cellContent: 'Instance Key',
                    isBold: true,
                    width: '25%',
                  },
                  {
                    cellContent: 'Affected Tokens',
                    isBold: true,
                    width: '20%',
                  },
                  {
                    cellContent: '',
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
                      columns: [
                        {
                          id: 'operation',
                          cellContent:
                            OPERATION_DISPLAY_NAME[modification.operation],
                        },
                        {
                          id: 'flowNode',
                          cellContent: (
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
                        },
                        {
                          id: 'instanceKey',
                          cellContent: (
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
                        },
                        {
                          id: 'affectedTokens',
                          dataTestId: 'affected-token-count',
                          cellContent:
                            modification.operation === 'CANCEL_TOKEN'
                              ? modification.visibleAffectedTokenCount +
                                (modificationsStore.modificationsByFlowNode[
                                  flowNodeId
                                ]?.cancelledChildTokens ?? 0)
                              : modification.affectedTokenCount,
                        },
                        {
                          id: 'delete',
                          cellContent: (
                            <ActionButton
                              title="Delete flow node modification"
                              onClick={() => {
                                modificationsStore.removeFlowNodeModification(
                                  modification
                                );
                              }}
                              icon={<DeleteIcon />}
                            />
                          ),
                        },
                      ],
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
                hasFixedColumnWidths
                headerColumns={[
                  {
                    cellContent: 'Operation',
                    isBold: true,
                    width: '25%',
                  },
                  {
                    cellContent: 'Scope',
                    isBold: true,
                    width: '25%',
                  },
                  {
                    cellContent: 'Name / Value',
                    isBold: true,
                    width: '25%',
                  },
                  {
                    cellContent: '',
                    width: '20%',
                  },
                  {
                    cellContent: '',
                    width: '5%',
                  },
                ]}
                rows={modificationsStore.variableModifications.map(
                  ({
                    operation,
                    flowNodeName,
                    name,
                    oldValue,
                    newValue,
                    scopeId,
                    id,
                  }) => {
                    return {
                      id: `${scopeId}${id}`,
                      columns: [
                        {
                          id: 'operation',
                          cellContent: OPERATION_DISPLAY_NAME[operation],
                        },
                        {
                          id: 'flowNode',
                          cellContent: (
                            <TruncatedValue>{flowNodeName}</TruncatedValue>
                          ),
                        },
                        {
                          id: 'nameValuePair',
                          cellContent: (
                            <VariableModification
                              operation={operation}
                              name={name}
                              oldValue={oldValue ?? ''}
                              newValue={newValue}
                            />
                          ),
                        },
                        {
                          id: 'empty-cell',
                          cellContent: '',
                        },
                        {
                          id: 'delete',
                          cellContent: (
                            <ActionButton
                              title="Delete variable modification"
                              onClick={() => {
                                modificationsStore.removeVariableModification(
                                  scopeId,
                                  id,
                                  operation,
                                  'summaryModal'
                                );
                              }}
                              icon={<DeleteIcon />}
                            />
                          ),
                        },
                      ],
                    };
                  }
                )}
              />
            )}
          </Container>
        }
        footer={
          <>
            <Modal.SecondaryButton title="Cancel" onClick={onClose}>
              Cancel
            </Modal.SecondaryButton>
            <Modal.PrimaryButton
              title="Apply"
              onClick={() => {
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
                  onError: () => {
                    tracking.track({eventName: 'modification-failed'});
                    notifications.displayNotification('error', {
                      headline: 'Modification failed',
                      description:
                        'Unable to apply modifications, please try again.',
                    });
                  },
                });
                onClose();
              }}
              data-testid="apply-button"
              disabled={
                areModificationsInvalid ||
                modificationsStore.state.modifications.length === 0
              }
            >
              Apply
            </Modal.PrimaryButton>
          </>
        }
      />
    );
  }
);

export {ModificationSummaryModal};
