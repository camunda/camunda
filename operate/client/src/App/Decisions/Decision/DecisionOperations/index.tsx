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
import {DeleteButtonContainer} from 'modules/components/DeleteDefinition/styled';
import {InlineLoading, Link, ListItem, Stack} from '@carbon/react';
import {DeleteDefinitionModal} from 'modules/components/DeleteDefinitionModal';
import {StructuredList} from 'modules/components/StructuredList';
import {UnorderedList} from 'modules/components/DeleteDefinitionModal/Warning/styled';
import {decisionDefinitionStore} from 'modules/stores/decisionDefinition';
import {notificationsStore} from 'modules/stores/notifications';
import {tracking} from 'modules/tracking';
import {handleOperationError} from 'modules/utils/notifications';
import {observer} from 'mobx-react';
import {useDeleteResource} from 'modules/mutations/resource/useDeleteResource';

type Props = {
  decisionRequirementsKey: string;
  decisionName: string;
  decisionVersion: number;
};

const DecisionOperations: React.FC<Props> = observer(
  ({decisionRequirementsKey, decisionName, decisionVersion}) => {
    const [isDeleteModalVisible, setIsDeleteModalVisible] =
      useState<boolean>(false);

    const deleteResourceMutation = useDeleteResource(
      decisionRequirementsKey,
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

    const isOperationRunning = deleteResourceMutation.isPending;

    return (
      <>
        <DeleteButtonContainer>
          {isOperationRunning && (
            <InlineLoading data-testid="delete-operation-spinner" />
          )}
          <OperationItems>
            <DangerButton
              title={`Delete Decision Definition "${decisionName} - Version ${decisionVersion}"`}
              type="DELETE"
              disabled={isOperationRunning}
              onClick={() => {
                tracking.track({
                  eventName: 'definition-deletion-button',
                  resource: 'decision',
                  version: decisionVersion.toString(),
                });
                setIsDeleteModalVisible(true);
              }}
            />
          </OperationItems>
        </DeleteButtonContainer>
        <DeleteDefinitionModal
          title="Delete DRD"
          description="You are about to delete the following DRD:"
          confirmationText="Yes, I confirm I want to delete this DRD and all related instances."
          isVisible={isDeleteModalVisible}
          warningTitle="Deleting a decision definition will delete the DRD and will impact
          the following:"
          warningContent={
            <Stack gap={6}>
              <UnorderedList nested>
                <ListItem>
                  By deleting a decision definition, you will be deleting the
                  DRD which contains this decision definition. All other
                  decision tables and literal expressions that are part of the
                  DRD will also be deleted.
                </ListItem>
                <ListItem>
                  Deleting the only existing version of a decision definition
                  could result in process incidents.
                </ListItem>
              </UnorderedList>
              <Link
                href="https://docs.camunda.io/docs/components/operate/userguide/delete-resources/"
                target="_blank"
              >
                Read more about deleting a decision definition
              </Link>
            </Stack>
          }
          bodyContent={
            <StructuredList
              headerColumns={[
                {
                  cellContent: 'DRD name',
                },
              ]}
              rows={[
                {
                  key: decisionDefinitionStore.name,
                  columns: [
                    {
                      cellContent: decisionDefinitionStore.name,
                    },
                  ],
                },
              ]}
              label="DRD Details"
            />
          }
          onClose={() => setIsDeleteModalVisible(false)}
          onDelete={() => {
            setIsDeleteModalVisible(false);

            tracking.track({
              eventName: 'definition-deletion-confirmation',
              resource: 'decision',
              version: decisionVersion.toString(),
            });

            deleteResourceMutation.mutate();
          }}
        />
      </>
    );
  },
);

export {DecisionOperations};
