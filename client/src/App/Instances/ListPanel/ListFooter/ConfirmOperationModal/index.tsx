/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import Modal, {SIZES} from 'modules/components/Modal';
import * as Styled from './styled';

type Props = {
  onModalClose: (...args: any[]) => any;
  isVisible: boolean;
  onApplyClick: (...args: any[]) => any;
  onCancelClick: (...args: any[]) => any;
  bodyText: string;
};

function ConfirmOperationModal({
  onModalClose,
  isVisible,
  bodyText,
  onApplyClick,
  onCancelClick,
}: Props) {
  return (
    <Modal onModalClose={onModalClose} isVisible={isVisible} size={SIZES.SMALL}>
      <Modal.Header>Apply Operation</Modal.Header>
      <Modal.Body>
        <Styled.BodyText>
          <div>{bodyText}</div>
          <div>Click "Apply" to proceed.</div>
        </Styled.BodyText>
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
}

export default ConfirmOperationModal;
