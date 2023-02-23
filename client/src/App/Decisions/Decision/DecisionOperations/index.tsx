/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState} from 'react';
import {OperationItem} from 'modules/components/OperationItem';
import {OperationItems} from 'modules/components/OperationItems';
import {decisionDefinitionStore} from 'modules/stores/decisionDefinition';
import {DeleteDefinitionModal} from 'modules/components/DeleteDefinitionModal';
import {DetailTable} from 'modules/components/DeleteDefinitionModal/DetailTable';

import {panelStatesStore} from 'modules/stores/panelStates';
import {operationsStore} from 'modules/stores/operations';
import {useNotifications} from 'modules/notifications';

type Props = {
  decisionDefinitionId: string;
  decisionName: string;
  decisionVersion: string;
};

const DecisionOperations: React.FC<Props> = ({
  decisionDefinitionId,
  decisionName,
  decisionVersion,
}) => {
  const [isDeleteModalVisible, setIsDeleteModalVisible] =
    useState<boolean>(false);

  const notifications = useNotifications();

  return (
    <>
      <OperationItems>
        <OperationItem
          title={`Delete Decision Definition "${decisionName} - Version ${decisionVersion}"`}
          type="DELETE"
          onClick={() => setIsDeleteModalVisible(true)}
        />
      </OperationItems>

      <DeleteDefinitionModal
        title="Delete DRD"
        description="You are about to delete the following DRD:"
        confirmationText="Yes, I confirm I want to delete this DRD and all related instances."
        isVisible={isDeleteModalVisible}
        bodyContent={
          <DetailTable
            headerColumns={[
              {
                cellContent: 'DRD name',
              },
            ]}
            rows={[
              {
                columns: [{cellContent: decisionDefinitionStore.name}],
              },
            ]}
          />
        }
        onClose={() => setIsDeleteModalVisible(false)}
        onDelete={() => {
          setIsDeleteModalVisible(false);

          operationsStore.applyDeleteDecisionDefinitionOperation({
            decisionDefinitionId,
            onSuccess: panelStatesStore.expandOperationsPanel,
            onError: () =>
              notifications.displayNotification('error', {
                headline: 'Operation could not be created',
              }),
          });
        }}
      />
    </>
  );
};

export {DecisionOperations};
