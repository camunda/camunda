/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import Modal, {SIZES} from 'modules/components/Modal';

type Props = {
  isVisible: boolean;
  title: string | null;
  content: string | null;
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
      <Modal.Body>
        <Modal.BodyText>{content}</Modal.BodyText>
      </Modal.Body>
      <Modal.Footer>
        <Modal.PrimaryButton title="Close Modal" onClick={onModalClose}>
          Close
        </Modal.PrimaryButton>
      </Modal.Footer>
    </Modal>
  );
};

export {ErrorMessageModal};
