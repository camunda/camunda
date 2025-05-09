/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useState} from 'react';

import {ACTIVE_OPERATION_STATES} from 'modules/constants';
import {ErrorHandler, operationsStore} from 'modules/stores/operations';
import {observer} from 'mobx-react';

import {OperationItems} from 'modules/components/OperationItems';
import {OperationItem} from 'modules/components/OperationItem';
import {DangerButton} from 'modules/components/OperationItem/DangerButton';
import {modificationsStore} from 'modules/stores/modifications';
import {Restricted} from 'modules/components/Restricted';
import {Modal, InlineLoading} from '@carbon/react';
import {Paths} from 'modules/Routes';
import {Link} from 'modules/components/Link';
import {OperationsContainer} from '../styled';
import {processInstancesStore} from 'modules/stores/processInstances';
import {getStateLocally} from 'modules/utils/localStorage';
import {ModificationHelperModal} from '../ModificationHelperModal';
import {ProcessInstance} from '@vzeta/camunda-api-zod-schemas/operate';
import {useOperations} from 'modules/queries/operations/useOperations';
import {useRootInstanceId} from 'modules/queries/callHierarchy/useRootInstanceId';

type Props = {
  instance: ProcessInstance;
  onOperation?: (operationType: OperationEntityType) => void;
  onError?: ErrorHandler;
  onSuccess?: (operationType: OperationEntityType) => void;
  forceSpinner?: boolean;
  isInstanceModificationVisible?: boolean;
  permissions?: ResourceBasedPermissionDto[] | null;
};

const Operations: React.FC<Props> = observer(
  ({
    instance,
    onOperation,
    onError,
    onSuccess,
    forceSpinner,
    isInstanceModificationVisible = false,
    permissions,
  }) => {
    const [isDeleteModalVisible, setIsDeleteModalVisible] = useState(false);
    const [isCancellationModalVisible, setIsCancellationModalVisible] =
      useState(false);
    const [
      isModificationModeHelperModalVisible,
      setIsModificationModeHelperModalVisible,
    ] = useState(false);

    const {data: operations} = useOperations();
    const {data: rootInstanceId} = useRootInstanceId();

    const {isModificationModeEnabled} = modificationsStore;

    const applyOperation = async (
      operationType: InstanceOperationEntity['type'],
    ) => {
      await operationsStore.applyOperation({
        instanceId: instance.processInstanceKey,
        payload: {
          operationType,
        },
        onError,
        onSuccess,
      });

      onOperation?.(operationType);
    };

    const isOperationActive = (operationType: OperationEntityType) => {
      return operations?.some(
        (operation) =>
          operation.type === operationType &&
          ACTIVE_OPERATION_STATES.includes(operation.state),
      );
    };

    const isRunning = instance.state === 'ACTIVE';

    return (
      <OperationsContainer orientation="horizontal">
        {(forceSpinner ||
          processInstancesStore.processInstanceIdsWithActiveOperations.includes(
            instance.processInstanceKey,
          )) && (
          <InlineLoading
            data-testid="operation-spinner"
            title={`Instance ${instance.processInstanceKey} has scheduled Operations`}
          />
        )}
        <OperationItems>
          {instance.hasIncident && !isModificationModeEnabled && (
            <Restricted
              resourceBasedRestrictions={{
                scopes: ['UPDATE_PROCESS_INSTANCE'],
                permissions,
              }}
            >
              <OperationItem
                type="RESOLVE_INCIDENT"
                onClick={() => applyOperation('RESOLVE_INCIDENT')}
                title={`Retry Instance ${instance.processInstanceKey}`}
                disabled={isOperationActive('RESOLVE_INCIDENT')}
                size="sm"
              />
            </Restricted>
          )}
          {isRunning && !isModificationModeEnabled && (
            <Restricted
              resourceBasedRestrictions={{
                scopes: ['UPDATE_PROCESS_INSTANCE'],
                permissions,
              }}
            >
              <OperationItem
                type="CANCEL_PROCESS_INSTANCE"
                onClick={() => setIsCancellationModalVisible(true)}
                title={`Cancel Instance ${instance.processInstanceKey}`}
                disabled={isOperationActive('CANCEL_PROCESS_INSTANCE')}
                size="sm"
              />
            </Restricted>
          )}
          {!isRunning && (
            <Restricted
              resourceBasedRestrictions={{
                scopes: ['DELETE_PROCESS_INSTANCE'],
                permissions,
              }}
            >
              <DangerButton
                type="DELETE"
                onClick={() => setIsDeleteModalVisible(true)}
                title={`Delete Instance ${instance.processInstanceKey}`}
                disabled={isOperationActive('DELETE_PROCESS_INSTANCE')}
                size="sm"
              />
            </Restricted>
          )}

          {isInstanceModificationVisible &&
            isRunning &&
            !isModificationModeEnabled && (
              <Restricted
                resourceBasedRestrictions={{
                  scopes: ['UPDATE_PROCESS_INSTANCE'],
                  permissions,
                }}
              >
                <OperationItem
                  type="ENTER_MODIFICATION_MODE"
                  onClick={() => {
                    if (getStateLocally()?.hideModificationHelperModal) {
                      modificationsStore.enableModificationMode();
                    } else {
                      setIsModificationModeHelperModalVisible(true);
                    }
                  }}
                  title={`Modify Instance ${instance.processInstanceKey}`}
                  size="sm"
                />
              </Restricted>
            )}
        </OperationItems>

        {isCancellationModalVisible && (
          <>
            {!rootInstanceId ? (
              <Modal
                open={isCancellationModalVisible}
                preventCloseOnClickOutside
                modalHeading="Apply Operation"
                primaryButtonText="Apply"
                secondaryButtonText="Cancel"
                onRequestSubmit={() => {
                  setIsCancellationModalVisible(false);
                  applyOperation('CANCEL_PROCESS_INSTANCE');
                }}
                onRequestClose={() => setIsCancellationModalVisible(false)}
                size="md"
                data-testid="confirm-cancellation-modal"
              >
                <p>{`About to cancel Instance ${instance.processInstanceKey}. In case there are called instances, these will be canceled too.`}</p>
                <p>Click "Apply" to proceed.</p>
              </Modal>
            ) : (
              <Modal
                open={isCancellationModalVisible}
                preventCloseOnClickOutside
                modalHeading="Cancel Instance"
                passiveModal
                onRequestClose={() => setIsCancellationModalVisible(false)}
                size="md"
                data-testid="passive-cancellation-modal"
              >
                <p>
                  To cancel this instance, the root instance{' '}
                  <Link
                    to={Paths.processInstance(rootInstanceId)}
                    title={`View root instance ${rootInstanceId}`}
                  >
                    {rootInstanceId}
                  </Link>{' '}
                  needs to be canceled. When the root instance is canceled all
                  the called instances will be canceled automatically.
                </p>
              </Modal>
            )}
          </>
        )}
        {isDeleteModalVisible && (
          <Modal
            open={isDeleteModalVisible}
            danger
            preventCloseOnClickOutside
            modalHeading="Delete Instance"
            primaryButtonText="Delete"
            secondaryButtonText="Cancel"
            onRequestSubmit={() => {
              applyOperation('DELETE_PROCESS_INSTANCE');
              setIsDeleteModalVisible(false);
            }}
            onRequestClose={() => setIsDeleteModalVisible(false)}
            size="md"
            data-testid="confirm-deletion-modal"
          >
            <p>About to delete Instance {instance.processInstanceKey}.</p>
            <p>Click "Delete" to proceed.</p>
          </Modal>
        )}

        {isModificationModeHelperModalVisible && (
          <ModificationHelperModal
            isVisible={isModificationModeHelperModalVisible}
            onClose={() => {
              setIsModificationModeHelperModalVisible(false);
            }}
            onSubmit={() => {
              setIsModificationModeHelperModalVisible(false);
              modificationsStore.enableModificationMode();
            }}
          />
        )}
      </OperationsContainer>
    );
  },
);

export {Operations};
