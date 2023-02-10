/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState} from 'react';

import {ACTIVE_OPERATION_STATES} from 'modules/constants';
import {operationsStore} from 'modules/stores/operations';
import {processInstancesStore} from 'modules/stores/processInstances';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {observer} from 'mobx-react';

import {hasIncident, isRunning} from 'modules/utils/instance';

import {OperationItems} from 'modules/components/OperationItems';
import {OperationItem} from 'modules/components/OperationItem';
import {OperationSpinner} from 'modules/components/OperationSpinner';
import {DeleteOperationModal} from './DeleteOperationModal';
import {ConfirmOperationModal} from 'modules/components/ConfirmOperationModal';
import {OperationsContainer} from './styled';
import {CalledInstanceCancellationModal} from './CalledInstanceCancellationModal';
import {modificationsStore} from 'modules/stores/modifications';
import {ModificationHelperModal} from './ModificationHelperModal';
import {getStateLocally} from 'modules/utils/localStorage';

type Props = {
  instance: ProcessInstanceEntity;
  onOperation?: (operationType: OperationEntityType) => void;
  onError?: (operationType: OperationEntityType) => void;
  onSuccess?: (operationType: OperationEntityType) => void;
  forceSpinner?: boolean;
  isInstanceModificationVisible?: boolean;
};

const Operations: React.FC<Props> = observer(
  ({
    instance,
    onOperation,
    onError,
    onSuccess,
    forceSpinner,
    isInstanceModificationVisible = false,
  }) => {
    const [isDeleteModalVisible, setIsDeleteModalVisible] = useState(false);
    const [isCancellationModalVisible, setIsCancellationModalVisible] =
      useState(false);
    const [
      isModificationModeHelperModalVisible,
      setIsModificationModeHelperModalVisible,
    ] = useState(false);

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

    const isSelected = processInstancesSelectionStore.isProcessInstanceChecked(
      instance.id
    );

    return (
      <OperationsContainer>
        {(forceSpinner ||
          processInstancesStore.processInstanceIdsWithActiveOperations.includes(
            instance.id
          )) && (
          <OperationSpinner
            isSelected={isSelected}
            title={`Instance ${instance.id} has scheduled Operations`}
            data-testid="operation-spinner"
          />
        )}

        <OperationItems>
          {hasIncident(instance) && !isModificationModeEnabled && (
            <OperationItem
              type="RESOLVE_INCIDENT"
              onClick={() => applyOperation('RESOLVE_INCIDENT')}
              title={`Retry Instance ${instance.id}`}
              disabled={isOperationActive('RESOLVE_INCIDENT')}
            />
          )}
          {isRunning(instance) && !isModificationModeEnabled && (
            <OperationItem
              type="CANCEL_PROCESS_INSTANCE"
              onClick={() => setIsCancellationModalVisible(true)}
              title={`Cancel Instance ${instance.id}`}
              disabled={isOperationActive('CANCEL_PROCESS_INSTANCE')}
            />
          )}
          {!isRunning(instance) && (
            <OperationItem
              type="DELETE"
              onClick={() => setIsDeleteModalVisible(true)}
              title={`Delete Instance ${instance.id}`}
              disabled={isOperationActive('DELETE_PROCESS_INSTANCE')}
            />
          )}
          {isInstanceModificationVisible &&
            isRunning(instance) &&
            !isModificationModeEnabled && (
              <OperationItem
                type="ENTER_MODIFICATION_MODE"
                onClick={() => {
                  if (getStateLocally()?.['hideModificationHelperModal']) {
                    modificationsStore.enableModificationMode();
                  } else {
                    setIsModificationModeHelperModalVisible(true);
                  }
                }}
                title={`Modify Instance ${instance.id}`}
              />
            )}
        </OperationItems>

        {instance.rootInstanceId === null ? (
          <ConfirmOperationModal
            bodyText={`About to cancel Instance ${instance.id}. In case there are called instances, these will be canceled too.`}
            onApplyClick={() => {
              setIsCancellationModalVisible(false);
              applyOperation('CANCEL_PROCESS_INSTANCE');
            }}
            isVisible={isCancellationModalVisible}
            onModalClose={() => setIsCancellationModalVisible(false)}
            onCancelClick={() => setIsCancellationModalVisible(false)}
          />
        ) : (
          <CalledInstanceCancellationModal
            onModalClose={() => setIsCancellationModalVisible(false)}
            isVisible={isCancellationModalVisible}
            rootInstanceId={instance.rootInstanceId}
          />
        )}
        <DeleteOperationModal
          isVisible={isDeleteModalVisible}
          onModalClose={() => setIsDeleteModalVisible(false)}
          instanceId={instance.id}
          onDeleteClick={() => {
            applyOperation('DELETE_PROCESS_INSTANCE');
            setIsDeleteModalVisible(false);
          }}
        />
        <ModificationHelperModal
          isVisible={isModificationModeHelperModalVisible}
          onClose={() => {
            setIsModificationModeHelperModalVisible(false);
            modificationsStore.enableModificationMode();
          }}
        />
      </OperationsContainer>
    );
  }
);

export {Operations};
