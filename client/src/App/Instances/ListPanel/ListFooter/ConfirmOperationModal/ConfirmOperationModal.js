/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import Modal, {SIZES} from 'modules/components/Modal';
import * as Styled from './styled';

function ConfirmOperationModal({
  onModalClose,
  isVisible,
  bodyText,
  onApplyClick,
  onCancelClick,
}) {
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

ConfirmOperationModal.propTypes = {
  onModalClose: PropTypes.func.isRequired,
  isVisible: PropTypes.bool.isRequired,
  onApplyClick: PropTypes.func.isRequired,
  onCancelClick: PropTypes.func.isRequired,
  bodyText: PropTypes.string.isRequired,
};

export default ConfirmOperationModal;
