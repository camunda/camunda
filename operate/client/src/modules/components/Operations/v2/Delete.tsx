/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useState} from 'react';
import {Modal} from '@carbon/react';
import {ProcessInstance} from '@vzeta/camunda-api-zod-schemas';
import {Restricted} from 'modules/components/Restricted';
import {useOperations} from 'modules/queries/operations/useOperations';
import {ACTIVE_OPERATION_STATES} from 'modules/constants';
import {DangerButton} from 'modules/components/OperationItem/DangerButton';

type Props = {
  processInstanceKey: ProcessInstance['processInstanceKey'];
  permissions?: ResourceBasedPermissionDto[] | null;
  applyOperation: (operationType: OperationEntityType) => Promise<void>;
};

const Delete: React.FC<Props> = ({
  processInstanceKey,
  applyOperation,
  permissions,
}) => {
  const [isDeleteModalVisible, setIsDeleteModalVisible] = useState(false);
  const {data: operations} = useOperations();

  const isOperationActive = (operationType: OperationEntityType) => {
    return operations?.some(
      (operation) =>
        operation.type === operationType &&
        ACTIVE_OPERATION_STATES.includes(operation.state),
    );
  };

  return (
    <>
      <Restricted
        resourceBasedRestrictions={{
          scopes: ['DELETE_PROCESS_INSTANCE'],
          permissions,
        }}
      >
        <DangerButton
          type="DELETE"
          onClick={() => setIsDeleteModalVisible(true)}
          title={`Delete Instance ${processInstanceKey}`}
          disabled={isOperationActive('DELETE_PROCESS_INSTANCE')}
          size="sm"
        />
      </Restricted>

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
          <p>About to delete Instance {processInstanceKey}.</p>
          <p>Click "Delete" to proceed.</p>
        </Modal>
      )}
    </>
  );
};

export {Delete};
