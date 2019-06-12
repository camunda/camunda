/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState} from 'react';

import PropTypes from 'prop-types';

import {isValidJSON} from 'modules/utils';
import {createBeautyfiedJSON, removeWhiteSpaces} from './service';

import CodeEditor from './CodeEditor';
import Modal from 'modules/components/Modal';

import * as Styled from './styled';

const MODE = {EDIT: 'edit', VIEW: 'view'};

function CodeModal({
  handleModalClose,
  handleModalSave,
  isModalVisible,
  mode,
  headline,
  initialValue
}) {
  const [newValue, setNewValue] = useState('');

  function onModalClose() {
    setNewValue('');
    handleModalClose();
  }

  function isValueModified() {
    return (
      newValue !==
      removeWhiteSpaces(
        isValidJSON(initialValue)
          ? createBeautyfiedJSON(initialValue)
          : initialValue
      )
    );
  }

  return (
    <Modal onModalClose={onModalClose} isVisible={isModalVisible}>
      <Modal.Header>{headline}</Modal.Header>
      <Styled.ModalBody>
        <CodeEditor
          initialValue={initialValue}
          handleChange={textContent => setNewValue(textContent)}
          contentEditable={mode === MODE.EDIT}
        />
      </Styled.ModalBody>
      <Modal.Footer>
        {mode === MODE.EDIT ? (
          <>
            <Modal.SecondaryButton title="Close Modal" onClick={onModalClose}>
              Close
            </Modal.SecondaryButton>
            <Modal.PrimaryButton
              title="Save Variable"
              data-test="save-btn"
              disableKeyBinding
              disabled={!newValue || !isValueModified()}
              onClick={() => handleModalSave(newValue)}
            >
              Save
            </Modal.PrimaryButton>
          </>
        ) : (
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
