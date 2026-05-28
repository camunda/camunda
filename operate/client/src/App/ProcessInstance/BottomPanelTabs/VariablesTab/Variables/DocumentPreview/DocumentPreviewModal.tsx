/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Modal} from '@carbon/react';
import type {StateProps} from 'modules/components/ModalStateManager';
import type {DocumentInfo} from '../DocumentValueCell/parseDocumentVariable';
import {PreviewImage, PreviewPdf} from './styled';
import {PreviewJson} from './PreviewJson';

type DocumentPreviewProps = {
  document: DocumentInfo;
};

const DocumentPreviewModal: React.FC<StateProps & DocumentPreviewProps> = ({
  open,
  setOpen,
  document,
}) => {
  return (
    <Modal
      open={open}
      onRequestClose={() => setOpen(false)}
      modalHeading={`Preview: ${document.fileName}`}
      size="lg"
      passiveModal
    >
      <ModalContent document={document} />
    </Modal>
  );
};

const ModalContent: React.FC<DocumentPreviewProps> = ({document}) => {
  if (document.type === 'image') {
    return <PreviewImage src={document.link} alt={document.fileName} />;
  }

  if (document.type === 'pdf') {
    return <PreviewPdf src={document.link} title={document.fileName} />;
  }

  if (document.type === 'json') {
    return <PreviewJson document={document} />;
  }

  return <p>Preview not available for document "{document.fileName}".</p>;
};

export {DocumentPreviewModal};
