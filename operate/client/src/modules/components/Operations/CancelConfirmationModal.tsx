/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Modal} from '@carbon/react';
import {type ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.10';
import {Paths} from 'modules/Routes';
import {Link} from 'modules/components/Link';
import {useCallHierarchy} from 'modules/queries/callHierarchy/useCallHierarchy';

type Props = {
  processInstanceKey: ProcessInstance['processInstanceKey'];
  open: boolean;
  onConfirm: () => void;
  onCancel: () => void;
};

const CancelConfirmationModal: React.FC<Props> = ({
  processInstanceKey,
  open,
  onConfirm,
  onCancel,
}) => {
  const {data: callHierarchy} = useCallHierarchy(
    {processInstanceKey},
    {enabled: open},
  );
  const rootInstanceId = callHierarchy?.[0]?.processInstanceKey;

  return !rootInstanceId ? (
    <Modal
      open={open}
      preventCloseOnClickOutside
      modalHeading="Apply Operation"
      primaryButtonText="Apply"
      secondaryButtonText="Cancel"
      onRequestSubmit={onConfirm}
      onRequestClose={onCancel}
      size="md"
      data-testid="confirm-cancellation-modal"
    >
      <p>
        About to cancel Instance {processInstanceKey}. In case there are called
        instances, these will be canceled too.
      </p>
      <p>Click &quot;Apply&quot; to proceed.</p>
    </Modal>
  ) : (
    <Modal
      open={open}
      preventCloseOnClickOutside
      modalHeading="Cancel Instance"
      passiveModal
      onRequestClose={onCancel}
      size="md"
      data-testid="passive-cancellation-modal"
    >
      <p>
        To cancel this instance, the root instance{' '}
        <Link
          to={Paths.processInstance(rootInstanceId)}
          title={`View root instance ${rootInstanceId}`}
        >
          {rootInstanceId}
        </Link>{' '}
        needs to be canceled. When the root instance is canceled all the called
        instances will be canceled automatically.
      </p>
    </Modal>
  );
};

export {CancelConfirmationModal};
