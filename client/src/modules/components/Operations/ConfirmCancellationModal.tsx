/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import Modal, {SIZES} from 'modules/components/Modal';

type Props = {
  onModalClose: () => void;
  isVisible: boolean;
  onApplyClick: () => void;
  instanceId: string;
};

const ConfirmCancellationModal: React.FC<Props> = ({
  onModalClose,
  isVisible,
  onApplyClick,
  instanceId,
}) => (
  <Modal onModalClose={onModalClose} isVisible={isVisible} size={SIZES.SMALL}>
    <Modal.Header>Cancel Instance</Modal.Header>
    <Modal.Body>
      <Modal.BodyText>
        {`About to cancel Instance ${instanceId}. In case there are called instances, these will be canceled too.`}
        <p> Click “Apply” to proceed.</p>
      </Modal.BodyText>
    </Modal.Body>
    <Modal.Footer>
      <Modal.SecondaryButton title="Cancel" onClick={onModalClose}>
        Cancel
      </Modal.SecondaryButton>
      <Modal.PrimaryButton title="Apply" onClick={onApplyClick}>
        Apply
      </Modal.PrimaryButton>
    </Modal.Footer>
  </Modal>
);

export {ConfirmCancellationModal};
