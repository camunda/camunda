/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import Modal, {SIZES} from 'modules/components/Modal';

const ErrorMessageModal = ({content, title, isVisible, toggleModal}) => {
  return (
    <Modal onModalClose={toggleModal} isVisible={isVisible} size={SIZES.BIG}>
      <Modal.Header>{title}</Modal.Header>
      <Modal.Body>
        <Modal.BodyText>{content}</Modal.BodyText>
      </Modal.Body>
      <Modal.Footer>
        <Modal.PrimaryButton title="Close Modal" onClick={toggleModal}>
          Close
        </Modal.PrimaryButton>
      </Modal.Footer>
    </Modal>
  );
};

ErrorMessageModal.propTypes = {
  isVisible: PropTypes.bool,
  title: PropTypes.string,
  content: PropTypes.string,
  toggleModal: PropTypes.func,
};

export {ErrorMessageModal};
