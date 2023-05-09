/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState} from 'react';

import {ACTIVE_OPERATION_STATES} from 'modules/constants';
import {ErrorHandler, operationsStore} from 'modules/stores/operations';
import {observer} from 'mobx-react';

import {hasIncident, isRunning} from 'modules/utils/instance';

import {OperationItems} from 'modules/components/Carbon/OperationItems';
import {OperationItem} from 'modules/components/Carbon/OperationItem';
import {DangerButton} from 'modules/components/Carbon/OperationItem/DangerButton';
import {modificationsStore} from 'modules/stores/modifications';
import {Restricted} from 'modules/components/Restricted';
import {Modal, InlineLoading} from '@carbon/react';
import {CarbonPaths} from 'modules/carbonRoutes';
import {Link} from 'modules/components/Carbon/Link';
import {OperationsContainer} from './styled';
import {processInstancesStore} from 'modules/stores/processInstances';

type Props = {
  instance: ProcessInstanceEntity;
  onOperation?: (operationType: OperationEntityType) => void;
  onError?: ErrorHandler;
  onSuccess?: (operationType: OperationEntityType) => void;
  forceSpinner?: boolean;
  isInstanceModificationVisible?: boolean;
  permissions?: ResourceBasedPermissionDto[] | null;
};

const Operations: React.FC<Props> = observer(
  ({instance, onOperation, onError, onSuccess, forceSpinner, permissions}) => {
    const [isDeleteModalVisible, setIsDeleteModalVisible] = useState(false);
    const [isCancellationModalVisible, setIsCancellationModalVisible] =
      useState(false);

    const {isModificationModeEnabled} = modificationsStore;

    const applyOperation = async (
      operationType: InstanceOperationEntity['type']
    ) => {
      operationsStore.applyOperation({
        instanceId: instance.id,
        payload: {
          operationType,
        },
        onError,
        onSuccess,
      });

      onOperation?.(operationType);
    };

    const isOperationActive = (operationType: OperationEntityType) => {
      return instance.operations.some(
        (operation) =>
          operation.type === operationType &&
          ACTIVE_OPERATION_STATES.includes(operation.state)
      );
    };

    return (
      <OperationsContainer orientation="horizontal">
        {(forceSpinner ||
          processInstancesStore.processInstanceIdsWithActiveOperations.includes(
            instance.id
          )) && (
          <InlineLoading
            data-testid="operation-spinner"
            title={`Instance ${instance.id} has scheduled Operations`}
          />
        )}
        <OperationItems>
          {hasIncident(instance) && !isModificationModeEnabled && (
            <Restricted
              scopes={['write']}
              resourceBasedRestrictions={{
                scopes: ['UPDATE_PROCESS_INSTANCE'],
                permissions,
              }}
            >
              <OperationItem
                type="RESOLVE_INCIDENT"
                onClick={() => applyOperation('RESOLVE_INCIDENT')}
                title={`Retry Instance ${instance.id}`}
                disabled={isOperationActive('RESOLVE_INCIDENT')}
                size="sm"
              />
            </Restricted>
          )}
          {isRunning(instance) && !isModificationModeEnabled && (
            <Restricted
              scopes={['write']}
              resourceBasedRestrictions={{
                scopes: ['UPDATE_PROCESS_INSTANCE'],
                permissions,
              }}
            >
              <OperationItem
                type="CANCEL_PROCESS_INSTANCE"
                onClick={() => setIsCancellationModalVisible(true)}
                title={`Cancel Instance ${instance.id}`}
                disabled={isOperationActive('CANCEL_PROCESS_INSTANCE')}
                size="sm"
              />
            </Restricted>
          )}
          {!isRunning(instance) && (
            <Restricted
              scopes={['write']}
              resourceBasedRestrictions={{
                scopes: ['DELETE_PROCESS_INSTANCE'],
                permissions,
              }}
            >
              <DangerButton
                type="DELETE"
                onClick={() => setIsDeleteModalVisible(true)}
                title={`Delete Instance ${instance.id}`}
                disabled={isOperationActive('DELETE_PROCESS_INSTANCE')}
                size="sm"
              />
            </Restricted>
          )}
        </OperationItems>

        {isCancellationModalVisible && (
          <>
            {instance.rootInstanceId === null ? (
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
              >
                <p>{`About to cancel Instance ${instance.id}. In case there are called instances, these will be canceled too.`}</p>
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
              >
                <p>
                  To cancel this instance, the root instance{' '}
                  <Link
                    to={CarbonPaths.processInstance(instance.rootInstanceId)}
                    title={`View root instance ${instance.rootInstanceId}`}
                  >
                    {instance.rootInstanceId}
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
          >
            <p>About to delete Instance {instance.id}.</p>
            <p>Click "Delete" to proceed.</p>
          </Modal>
        )}
      </OperationsContainer>
    );
  }
);

export {Operations};
