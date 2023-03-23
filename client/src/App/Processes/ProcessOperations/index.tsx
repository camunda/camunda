/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useState} from 'react';
import {OperationItem} from 'modules/components/OperationItem';
import {OperationItems} from 'modules/components/OperationItems';
import {DeleteDefinitionModal} from 'modules/components/DeleteDefinitionModal';
import {DetailTable} from 'modules/components/DeleteDefinitionModal/DetailTable';
import {
  Warning,
  Information,
  Ul,
  Link,
} from 'modules/components/DeleteDefinitionModal/Warning/styled';
import {operationsStore} from 'modules/stores/operations';
import {panelStatesStore} from 'modules/stores/panelStates';
import {useNotifications} from 'modules/notifications';
import {OperationSpinner} from 'modules/components/OperationSpinner';
import {DeleteButtonContainer} from 'modules/components/DeleteDefinition/styled';

type Props = {
  processDefinitionId: string;
  processName: string;
  processVersion: string;
};

const ProcessOperations: React.FC<Props> = ({
  processDefinitionId,
  processName,
  processVersion,
}) => {
  const [isDeleteModalVisible, setIsDeleteModalVisible] =
    useState<boolean>(false);

  const notifications = useNotifications();
  const [isOperationRunning, setIsOperationRunning] = useState(false);

  useEffect(() => {
    return () => {
      setIsOperationRunning(false);
    };
  }, [processDefinitionId]);

  return (
    <>
      <DeleteButtonContainer>
        {isOperationRunning && (
          <OperationSpinner data-testid="delete-operation-spinner" />
        )}
        <OperationItems>
          <OperationItem
            title={`Delete Process Definition "${processName} - Version ${processVersion}"`}
            type="DELETE"
            disabled={isOperationRunning}
            onClick={() => setIsDeleteModalVisible(true)}
          />
        </OperationItems>
      </DeleteButtonContainer>
      <DeleteDefinitionModal
        title="Delete Process Definition"
        description="You are about to delete the following process definition:"
        isVisible={isDeleteModalVisible}
        bodyContent={
          <DetailTable
            headerColumns={[
              {
                cellContent: 'Process Definition',
              },
            ]}
            rows={[
              {
                columns: [
                  {cellContent: `${processName} - Version ${processVersion}`},
                ],
              },
            ]}
          />
        }
        warningContent={
          <Warning>
            <Information>
              Deleting a process definition will permanently remove it and will
              impact the following:
            </Information>
            <Ul>
              <li>
                All the deleted process definition’s running process instances
                will be immediately canceled and deleted.
              </li>
              <li>
                All the deleted process definition’s finished process instances
                will be deleted from the application.
              </li>
              <li>
                All decision and process instances referenced by the deleted
                process instances will be deleted.
              </li>
              <li>
                If a process definition contains user tasks, they will be
                canceled and deleted from Tasklist.
              </li>
            </Ul>
            <Link
              href="https://docs.camunda.io/docs/components/operate/operate-introduction/"
              target="_blank"
            >
              For a detailed overview, please view our guide on deleting a
              process definition
            </Link>
          </Warning>
        }
        confirmationText="Yes, I confirm I want to delete this process definition."
        onClose={() => setIsDeleteModalVisible(false)}
        onDelete={() => {
          setIsOperationRunning(true);
          setIsDeleteModalVisible(false);
          operationsStore.applyDeleteProcessDefinitionOperation({
            processDefinitionId,
            onSuccess: panelStatesStore.expandOperationsPanel,
            onError: (statusCode: number) => {
              setIsOperationRunning(false);

              notifications.displayNotification('error', {
                headline: 'Operation could not be created',
                description:
                  statusCode === 403 ? 'You do not have permission' : undefined,
              });
            },
          });
        }}
      />
    </>
  );
};

export {ProcessOperations};
