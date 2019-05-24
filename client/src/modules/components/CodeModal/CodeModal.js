/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect, useState} from 'react';

import PropTypes from 'prop-types';

import {isValidJSON} from 'modules/utils';

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

  useEffect(() => {
    isModalVisible &&
      mode === MODE.EDIT &&
      document
        .getElementById('code')
        .addEventListener('input', function({target}) {
          setNewValue(isValidJSON(target.innerText) ? target.innerText : '');
        });
  });

  function onModalClose() {
    setNewValue('');
    handleModalClose();
  }

  function createBeautyfiedJSON(validJSONstring, indentationSpace = 0) {
    // removes all possible spaces, a user could have added during in-line edit.
    const validObject = JSON.parse(validJSONstring);
    return JSON.stringify(validObject, null, indentationSpace);
  }

  function isValueModified() {
    const sanatizedNewValueString = newValue
      // remove all line breaks
      .replace(/\r?\n|\r/g, '')
      // remove all white space
      .replace(/\s/g, '');

    const sanatizedInitialValueString = isValidJSON(initialValue)
      ? createBeautyfiedJSON(initialValue).replace(/\s/g, '')
      : initialValue.replace(/\s/g, '');

    return sanatizedNewValueString !== sanatizedInitialValueString;
  }

  function renderCodeLines(initialValue) {
    return initialValue.split('\n').map((line, idx) => (
      <Styled.CodeLine data-test={`codeline-${idx}`} key={idx}>
        {line}
      </Styled.CodeLine>
    ));
  }

  function renderEditButtons() {
    return (
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
    );
  }

  function renderViewButtons() {
    return (
      <Modal.PrimaryButton
        data-test="primary-close-btn"
        title="Close Modal"
        onClick={onModalClose}
      >
        Close
      </Modal.PrimaryButton>
    );
  }

  return (
    <Modal onModalClose={onModalClose} isVisible={isModalVisible}>
      <Modal.Header>{headline}</Modal.Header>
      <Styled.ModalBody>
        <Styled.Pre>
          <code
            id="code"
            contentEditable={mode === MODE.EDIT}
            suppressContentEditableWarning
          >
            {renderCodeLines(
              isValidJSON(initialValue)
                ? createBeautyfiedJSON(initialValue, 2)
                : initialValue
            )}
          </code>
        </Styled.Pre>
      </Styled.ModalBody>
      <Modal.Footer>
        {mode === MODE.EDIT ? renderEditButtons() : renderViewButtons()}
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
