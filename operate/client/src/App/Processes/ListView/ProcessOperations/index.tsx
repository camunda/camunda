/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {DangerButton} from 'modules/components/OperationItem/DangerButton';
import {OperationItems} from 'modules/components/OperationItems';
import {DeleteButtonContainer} from './styled';
import {InlineLoading, Link, ListItem, Stack} from '@carbon/react';
import {DrainingTag} from 'modules/components/DrainingTag';
import {DeleteDefinitionModal} from 'modules/components/DeleteDefinitionModal';
import {StructuredList} from 'modules/components/StructuredList';
import {UnorderedList} from 'modules/components/DeleteDefinitionModal/Warning/styled';
import {notificationsStore} from 'modules/stores/notifications';
import {handleOperationError} from 'modules/utils/notifications';
import {tracking} from 'modules/tracking';
import {observer} from 'mobx-react';
import {useRunningInstancesCount} from 'modules/queries/processInstance/useRunningInstancesCount';
import {useDeleteResource} from 'modules/mutations/resource/useDeleteResource';
import {useDrainingProcessDefinitions} from 'modules/queries/processDefinitions/useDrainingProcessDefinitions';
import {DRAINING_MESSAGES} from 'modules/utils/draining';
import {DeletedTag} from 'modules/components/DeletedTag';
import type {ProcessDefinition} from '@camunda/camunda-api-zod-schemas/8.10';

type Props = {
  processDefinitionKey: string;
  processName: string;
  processVersion: number;
  processDefinitionState: ProcessDefinition['state'];
};

const ProcessOperations: React.FC<Props> = observer(
  ({
    processDefinitionKey,
    processName,
    processVersion,
    processDefinitionState,
  }) => {
    const [isDeleteModalVisible, setIsDeleteModalVisible] =
      useState<boolean>(false);

    const {data: runningInstancesCount} = useRunningInstancesCount({
      processDefinitionKey,
    });

    const deleteResourceMutation = useDeleteResource(
      processDefinitionKey,
      {deleteHistory: true},
      {
        onSuccess: () => {
          notificationsStore.displayNotification({
            kind: 'success',
            title: 'Operation created',
            isDismissable: true,
          });
        },
        onError: (error) => {
          handleOperationError(error?.response?.status);
        },
      },
    );

    const {data: draining} = useDrainingProcessDefinitions();
    const isDraining = !!draining?.byKey.has(processDefinitionKey);
    const isDeleted = processDefinitionState === 'DELETED';

    const isOperationRunning = deleteResourceMutation.isPending;

    return (
      <>
        <DeleteButtonContainer>
          {isOperationRunning && (
            <InlineLoading data-testid="delete-operation-spinner" />
          )}
          {isDraining ? (
            <DrainingTag
              description={DRAINING_MESSAGES.version}
              align="left-top"
            />
          ) : (
            <>
              {isDeleted && <DeletedTag align="left-top" />}
              <OperationItems>
                <DangerButton
                  title={
                    (runningInstancesCount ?? 0) > 0
                      ? 'Only process definitions without running instances can be deleted.'
                      : isDeleted
                        ? `Delete Process Definition History "${processName} - Version ${processVersion}"`
                        : `Delete Process Definition "${processName} - Version ${processVersion}"`
                  }
                  type="DELETE"
                  disabled={
                    isOperationRunning || (runningInstancesCount ?? 0) !== 0
                  }
                  onClick={() => {
                    tracking.track({
                      eventName: 'definition-deletion-button',
                      resource: 'process',
                      version: processVersion.toString(),
                    });

                    setIsDeleteModalVisible(true);
                  }}
                />
              </OperationItems>
            </>
          )}
        </DeleteButtonContainer>
        <DeleteDefinitionModal
          title="Delete Process Definition"
          description={
            isDeleted
              ? 'This process definition is already deleted. Continuing will permanently remove its remaining history:'
              : 'You are about to delete the following process definition:'
          }
          confirmationText={
            isDeleted
              ? 'Yes, I confirm I want to permanently delete this process definition history.'
              : 'Yes, I confirm I want to delete this process definition.'
          }
          isVisible={isDeleteModalVisible}
          warningTitle={
            isDeleted
              ? 'Deleting the remaining process definition history is permanent and will impact the following:'
              : 'Deleting a process definition will permanently remove it and will impact the following:'
          }
          warningContent={
            <Stack gap={6}>
              <UnorderedList nested>
                <ListItem>
                  All the deleted process definition's finished process
                  instances will be deleted from the application.
                </ListItem>
                <ListItem>
                  All decision and process instances referenced by the deleted
                  process instances will be deleted.
                </ListItem>
                <ListItem>
                  If a process definition contains user tasks, they will be
                  deleted from Tasklist.
                </ListItem>
              </UnorderedList>
              <Link
                href="https://docs.camunda.io/docs/components/operate/userguide/delete-resources/"
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
            setIsDeleteModalVisible(false);

            tracking.track({
              eventName: 'definition-deletion-confirmation',
              resource: 'process',
              version: processVersion.toString(),
            });

            deleteResourceMutation.mutate();
          }}
        />
      </>
    );
  },
);

export {ProcessOperations};
