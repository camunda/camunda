/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useState} from 'react';
import {DangerButton} from 'modules/components/Carbon/OperationItem/DangerButton';
import {OperationItems} from 'modules/components/Carbon/OperationItems';
import {DeleteButtonContainer} from 'modules/components/DeleteDefinition/styled';
import {InlineLoading, Link, ListItem, Stack} from '@carbon/react';
import {DeleteDefinitionModal} from 'modules/components/Carbon/DeleteDefinitionModal';
import {operationsStore} from 'modules/stores/operations';
import {panelStatesStore} from 'modules/stores/panelStates';
import {StructuredList} from 'modules/components/Carbon/StructuredList';
import {UnorderedList} from 'modules/components/Carbon/DeleteDefinitionModal/Warning/styled';
import {notificationsStore} from 'modules/stores/carbonNotifications';

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
          <InlineLoading data-testid="delete-operation-spinner" />
        )}
        <OperationItems>
          <DangerButton
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
        confirmationText="Yes, I confirm I want to delete this process definition."
        isVisible={isDeleteModalVisible}
        warningTitle="Deleting a process definition will permanently remove it and will
        impact the following:"
        warningContent={
          <Stack gap={6}>
            <UnorderedList nested>
              <ListItem>
                All the deleted process definition’s running process instances
                will be immediately canceled and deleted.
              </ListItem>
              <ListItem>
                All the deleted process definition’s finished process instances
                will be deleted from the application.
              </ListItem>
              <ListItem>
                All decision and process instances referenced by the deleted
                process instances will be deleted.
              </ListItem>
              <ListItem>
                If a process definition contains user tasks, they will be
                canceled and deleted from Tasklist.
              </ListItem>
            </UnorderedList>
            <Link
              href="https://docs.camunda.io/docs/components/operate/operate-introduction/"
              target="_blank"
            >
              For a detailed overview, please view our guide on deleting a
              process definition
            </Link>
          </Stack>
        }
        bodyContent={
          <StructuredList
            headerColumns={[
              {
                cellContent: 'Process Definition',
              },
            ]}
            rows={[
              {
                key: `${processName}-v${processVersion}`,
                columns: [
                  {cellContent: `${processName} - Version ${processVersion}`},
                ],
              },
            ]}
            label="Process Details"
          />
        }
        onClose={() => setIsDeleteModalVisible(false)}
        onDelete={() => {
          setIsOperationRunning(true);
          setIsDeleteModalVisible(false);
          operationsStore.applyDeleteProcessDefinitionOperation({
            processDefinitionId,
            onSuccess: panelStatesStore.expandOperationsPanel,
            onError: (statusCode: number) => {
              setIsOperationRunning(false);

              notificationsStore.displayNotification({
                kind: 'error',
                title: 'Operation could not be created',
                subtitle:
                  statusCode === 403 ? 'You do not have permission' : undefined,
                isDismissable: true,
              });
            },
          });
        }}
      />
    </>
  );
};

export {ProcessOperations};
