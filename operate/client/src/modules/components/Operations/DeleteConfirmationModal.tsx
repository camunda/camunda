/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Modal} from '@carbon/react';
import {type ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.10';

type Props = {
  processInstanceKey: ProcessInstance['processInstanceKey'];
  open: boolean;
  onConfirm: () => void;
  onCancel: () => void;
};

const DeleteConfirmationModal: React.FC<Props> = ({
  processInstanceKey,
  open,
  onConfirm,
  onCancel,
}) => {
  return (
    <Modal
      open={open}
      danger
      preventCloseOnClickOutside
      modalHeading="Delete Instance"
      primaryButtonText="Delete"
      secondaryButtonText="Cancel"
      onRequestSubmit={onConfirm}
      onRequestClose={onCancel}
      size="md"
      data-testid="confirm-deletion-modal"
    >
      <p>About to delete Instance {processInstanceKey}.</p>
      <p>Click &quot;Delete&quot; to proceed.</p>
    </Modal>
  );
};

export {DeleteConfirmationModal};
