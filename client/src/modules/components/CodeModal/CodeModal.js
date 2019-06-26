/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import PropTypes from 'prop-types';

import CodeEditor from './CodeEditor';
import Modal from 'modules/components/Modal';

import * as Styled from './styled';

const MODE = {EDIT: 'edit', VIEW: 'view'};

function CodeModal({
  handleModalClose,
  isModalVisible,
  mode,
  headline,
  initialValue
}) {
  function onModalClose() {
    handleModalClose();
  }

  return (
    <Modal onModalClose={onModalClose} isVisible={isModalVisible}>
      <Modal.Header>{headline}</Modal.Header>
      <Styled.ModalBody>
        <CodeEditor
          initialValue={initialValue}
          contentEditable={mode !== MODE.VIEW}
        />
      </Styled.ModalBody>
      <Modal.Footer>
        {mode === MODE.VIEW && (
          <Modal.PrimaryButton
            data-test="primary-close-btn"
            title="Close Modal"
            onClick={onModalClose}
          >
            Close
          </Modal.PrimaryButton>
        )}
      </Modal.Footer>
    </Modal>
  );
}

CodeModal.propTypes = {
  handleModalClose: PropTypes.func,
  handleModalSave: PropTypes.func,
  isModalVisible: PropTypes.bool,
  headline: PropTypes.string,
  initialValue: PropTypes.string,
  mode: PropTypes.oneOf(Object.values(MODE)).isRequired
};

export default CodeModal;
