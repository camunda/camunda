/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import Modal, {SIZES} from 'modules/components/Modal';
import CodeEditor from 'modules/components/CodeModal/CodeEditor';
import {ModalBody} from './styled';

type Props = {
  isVisible: boolean;
  title: string;
  content: string;
  onModalClose: () => void;
};

const ErrorMessageModal = ({
  content,
  title,
  isVisible,
  onModalClose,
}: Props) => {
  return (
    <Modal onModalClose={onModalClose} isVisible={isVisible} size={SIZES.BIG}>
      <Modal.Header>{title}</Modal.Header>
      <ModalBody>
        <CodeEditor initialValue={content} contentEditable={false} />
      </ModalBody>
      <Modal.Footer>
        <Modal.PrimaryButton title="Close Modal" onClick={onModalClose}>
          Close
        </Modal.PrimaryButton>
      </Modal.Footer>
    </Modal>
  );
};

export {ErrorMessageModal};
