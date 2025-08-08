/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Modal} from '@carbon/react';
import {type ProcessInstance} from '@vzeta/camunda-api-zod-schemas/8.8';
import {DangerButton} from 'modules/components/OperationItem/DangerButton';
type Props = {
  processInstanceKey: ProcessInstance['processInstanceKey'];
  onExecute: () => void;
  disabled?: boolean;
};

const Delete: React.FC<Props> = ({
  processInstanceKey,
  onExecute,
  disabled = false,
}) => {
  const [isDeleteModalVisible, setIsDeleteModalVisible] = useState(false);

  const confirmation = {
    title: 'Delete Instance',
    message: `About to delete Instance ${processInstanceKey}.`,
    primaryButtonText: 'Delete',
    secondaryButtonText: 'Cancel',
    isDangerous: true,
  };

  return (
    <>
      <DangerButton
        type="DELETE"
        onClick={() => setIsDeleteModalVisible(true)}
        title={`Delete Instance ${processInstanceKey}`}
        disabled={disabled}
        size="sm"
      />

      {isDeleteModalVisible && (
        <Modal
          open={isDeleteModalVisible}
          danger={confirmation.isDangerous}
          preventCloseOnClickOutside
          modalHeading={confirmation.title}
          primaryButtonText={confirmation.primaryButtonText}
          secondaryButtonText={confirmation.secondaryButtonText}
          onRequestSubmit={() => {
            onExecute();
            setIsDeleteModalVisible(false);
          }}
          onRequestClose={() => setIsDeleteModalVisible(false)}
          size="md"
          data-testid="confirm-deletion-modal"
        >
          <p>{confirmation.message}</p>
          <p>Click "{confirmation.primaryButtonText}" to proceed.</p>
        </Modal>
      )}
    </>
  );
};

export {Delete};
