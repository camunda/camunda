/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import Modal, {SIZES} from 'modules/components/Modal';
import {BodyText} from './styled';

type Props = {
  onModalClose: () => void;
  isVisible: boolean;
  onApplyClick: () => void;
  onCancelClick: () => void;
  bodyText: string;
};

const ConfirmOperationModal: React.FC<Props> = ({
  onModalClose,
  isVisible,
  onApplyClick,
  onCancelClick,
  bodyText,
}) => (
  <Modal onModalClose={onModalClose} isVisible={isVisible} size={SIZES.SMALL}>
    <Modal.Header>Apply Operation</Modal.Header>
    <Modal.Body>
      <BodyText>
        <div>{bodyText}</div>
        <div>Click "Apply" to proceed.</div>
      </BodyText>
    </Modal.Body>
    <Modal.Footer>
      <Modal.SecondaryButton title="Cancel" onClick={onCancelClick}>
        Cancel
      </Modal.SecondaryButton>
      <Modal.PrimaryButton title="Apply" onClick={onApplyClick}>
        Apply
      </Modal.PrimaryButton>
    </Modal.Footer>
  </Modal>
);

export {ConfirmOperationModal};
