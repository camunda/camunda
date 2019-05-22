/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import PropTypes from 'prop-types';

import Modal from 'modules/components/Modal';

import * as Styled from './styled';

function CodeModal({
  handleModalClose,
  handleModalSave,
  isModalVisible,
  headline,
  content
}) {
  return (
    <Modal onModalClose={handleModalClose} isVisible={isModalVisible}>
      <Modal.Header>{headline}</Modal.Header>
      <Styled.ModalBody>
        <pre>
          <Styled.LinesSeparator />
          <code>
            <Styled.CodeLine
              id="editor"
              contentEditable
              suppressContentEditableWarning
            >
              {content}
            </Styled.CodeLine>
          </code>
        </pre>
      </Styled.ModalBody>
      <Modal.Footer>
        <Modal.SecondaryButton title="Close Modal" onClick={handleModalClose}>
          Close
        </Modal.SecondaryButton>

        <Modal.PrimaryButton
          title="Save Variable"
          //   disabled={!isValidJSON(content)}
          onClick={handleModalSave}
        >
          Save
        </Modal.PrimaryButton>
      </Modal.Footer>
    </Modal>
  );
}

CodeModal.propTypes = {
  handleModalClose: PropTypes.func,
  handleModalSave: PropTypes.func,
  isModalVisible: PropTypes.bool,
  headline: PropTypes.string,
  content: PropTypes.string
};

export default CodeModal;
