/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import CodeEditor from './CodeEditor';
import Modal, {SIZES} from 'modules/components/Modal';

import * as Styled from './styled';

const MODE = {EDIT: 'edit', VIEW: 'view'};

type Props = {
  handleModalClose: (...args: any[]) => any;
  isModalVisible: boolean;
  headline?: string;
  initialValue?: string;
  mode: 'edit' | 'view';
};

function CodeModal({
  handleModalClose,
  isModalVisible,
  mode,
  headline,
  initialValue,
}: Props) {
  function onModalClose() {
    handleModalClose();
  }

  return (
    <Modal
      onModalClose={onModalClose}
      isVisible={isModalVisible}
      size={SIZES.BIG}
    >
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
            data-testid="primary-close-btn"
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

export default CodeModal;
