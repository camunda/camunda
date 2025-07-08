/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useState} from 'react';

import {type ErrorHandler, operationsStore} from 'modules/stores/operations';
import {observer} from 'mobx-react';

import {OperationItems} from 'modules/components/OperationItems';
import {OperationItem} from 'modules/components/OperationItem';
import {modificationsStore} from 'modules/stores/modifications';
import {Restricted} from 'modules/components/Restricted';
import {InlineLoading} from '@carbon/react';
import {OperationsContainer} from '../styled';
import {processInstancesStore} from 'modules/stores/processInstances';
import {getStateLocally} from 'modules/utils/localStorage';
import {ModificationHelperModal} from '../ModificationHelperModal';
import {type ProcessInstance} from '@vzeta/camunda-api-zod-schemas';
import {Cancel} from './Cancel';
import {Delete} from './Delete';
import {ResolveIncident} from './ResolveIncident';
import type {
  InstanceOperationEntity,
  ResourceBasedPermissionDto,
  OperationEntityType,
} from 'modules/types/operate';

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
    const [
      isModificationModeHelperModalVisible,
      setIsModificationModeHelperModalVisible,
    ] = useState(false);

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

    const isInstanceActive = instance.state === 'ACTIVE';

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
            <ResolveIncident
              processInstanceKey={instance.processInstanceKey}
              permissions={permissions}
              applyOperation={applyOperation}
            />
          )}
          {isInstanceActive && !isModificationModeEnabled && (
            <Cancel processInstanceKey={instance.processInstanceKey} />
          )}
          {!isInstanceActive && (
            <Delete
              processInstanceKey={instance.processInstanceKey}
              permissions={permissions}
              applyOperation={applyOperation}
            />
          )}

          {isInstanceModificationVisible &&
            isInstanceActive &&
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
