/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import React, {useState} from 'react';

import {ACTIVE_OPERATION_STATES} from 'modules/constants';
import {ErrorHandler, operationsStore} from 'modules/stores/operations';
import {observer} from 'mobx-react';

import {hasIncident, isRunning} from 'modules/utils/instance';

import {OperationItems} from 'modules/components/OperationItems';
import {OperationItem} from 'modules/components/OperationItem';
import {DangerButton} from 'modules/components/OperationItem/DangerButton';
import {modificationsStore} from 'modules/stores/modifications';
import {Restricted} from 'modules/components/Restricted';
import {Modal, InlineLoading} from '@carbon/react';
import {Paths} from 'modules/Routes';
import {Link} from 'modules/components/Link';
import {OperationsContainer} from './styled';
import {processInstancesStore} from 'modules/stores/processInstances';
import {getStateLocally} from 'modules/utils/localStorage';
import {ModificationHelperModal} from './ModificationHelperModal';

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

    const {isModificationModeEnabled} = modificationsStore;

    const applyOperation = async (
      operationType: InstanceOperationEntity['type'],
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
          ACTIVE_OPERATION_STATES.includes(operation.state),
      );
    };

    return (
      <OperationsContainer orientation="horizontal">
        {(forceSpinner ||
          processInstancesStore.processInstanceIdsWithActiveOperations.includes(
            instance.id,
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

          {isInstanceModificationVisible &&
            isRunning(instance) &&
            !isModificationModeEnabled && (
              <Restricted
                scopes={['write']}
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
                  title={`Modify Instance ${instance.id}`}
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
                data-testid="confirm-cancellation-modal"
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
                data-testid="passive-cancellation-modal"
              >
                <p>
                  To cancel this instance, the root instance{' '}
                  <Link
                    to={Paths.processInstance(instance.rootInstanceId)}
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
            data-testid="confirm-deletion-modal"
          >
            <p>About to delete Instance {instance.id}.</p>
            <p>Click "Delete" to proceed.</p>
          </Modal>
        )}

        {isModificationModeHelperModalVisible && (
          <ModificationHelperModal
            isVisible={isModificationModeHelperModalVisible}
            onClose={() => {
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
