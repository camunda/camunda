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
                    width: '35%',
                  },
                  {
                    cellContent: 'Affected Tokens',
                    isBold: true,
                    width: '35%',
                  },
                  {
                    cellContent: '',
                    width: '5%',
                  },
                ]}
                rows={modificationsStore.flowNodeModifications.map(
                  (modification, index) => {
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
                                  <TruncatedValue>
                                    {modification.flowNode.name}
                                  </TruncatedValue>
                                  &nbsp;→&nbsp;
                                  <TruncatedValue>
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
                          id: 'affectedTokens',
                          cellContent: modification.affectedTokenCount,
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
                    width: '35%',
                  },
                  {
                    cellContent: 'Name / Value',
                    isBold: true,
                    width: '35%',
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
                          id: 'delete',
                          cellContent: (
                            <ActionButton
                              title="Delete variable modification"
                              onClick={() => {
                                modificationsStore.removeVariableModification(
                                  scopeId,
                                  id,
                                  operation
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
              onClick={onClose}
              data-testid="apply-button"
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
