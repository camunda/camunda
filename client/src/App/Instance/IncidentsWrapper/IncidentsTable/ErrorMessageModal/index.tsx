/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import Modal, {SIZES} from 'modules/components/Modal';

type Props = {
  isVisible?: boolean;
  title?: string;
  content?: string;
  toggleModal?: (...args: any[]) => any;
};

const ErrorMessageModal = ({content, title, isVisible, toggleModal}: Props) => {
  return (
    // @ts-expect-error ts-migrate(2322) FIXME: Type 'undefined' is not assignable to type '(...ar... Remove this comment to see the full error message
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

export {ErrorMessageModal};
