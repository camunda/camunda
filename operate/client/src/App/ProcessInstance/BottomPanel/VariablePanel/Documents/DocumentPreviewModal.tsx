/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {lazy, Suspense, useEffect, useState} from 'react';
import {Modal} from '@carbon/react';
import {readFile} from './readFile';

const JSONEditorLazy = lazy(async () => {
  const [{loadMonaco}, {JSONEditor}] = await Promise.all([
    import('modules/loadMonaco'),
    import('modules/components/JSONEditor'),
  ]);

  loadMonaco();

  return {default: JSONEditor};
});

type Document = {
  id: string;
  name: string;
  type: string;
  size: number;
  path: string;
};

type Props = {
  document: Document;
  isOpen: boolean;
  onClose: () => void;
};

export const DocumentPreviewModal: React.FC<Props> = ({
  document,
  isOpen,
  onClose,
}) => {
  const [content, setContent] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const isJSON = document.type === 'application/json';

  useEffect(() => {
    if (isOpen && isJSON && !content && !isLoading) {
      setIsLoading(true);
      readFile(document.path)
        .then(setContent)
        .catch((error) => {
          console.error('Error loading file:', error);
          setContent('{}');
        })
        .finally(() => setIsLoading(false));
    }
  }, [isOpen, isJSON, content, isLoading, document.path]);

  if (!isOpen) {
    return null;
  }

  const isPDF = document.type === 'application/pdf';
  const isImage = document.type.startsWith('image/');

  let modalContent: React.ReactNode = null;

  if (isPDF) {
    modalContent = (
      <div style={{height: '70vh', width: '100%'}}>
        <iframe
          src={document.path}
          style={{width: '100%', height: '100%', border: 'none'}}
          title={document.name}
        />
      </div>
    );
  } else if (isImage) {
    modalContent = (
      <div
        style={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          height: '70vh',
        }}
      >
        <img
          src={document.path}
          alt={document.name}
          style={{maxWidth: '100%', maxHeight: '100%'}}
        />
      </div>
    );
  } else if (isJSON) {
    if (isLoading || !content) {
      return (
        <Modal
          open={isOpen}
          modalHeading={document.name}
          onRequestClose={onClose}
          passiveModal
        >
          <div>Loading...</div>
        </Modal>
      );
    }
    modalContent = (
      <Suspense>
        <JSONEditorLazy value={content} readOnly height="70vh" />
      </Suspense>
    );
  }

  return (
    <Modal
      open={isOpen}
      modalHeading={document.name}
      onRequestClose={onClose}
      passiveModal
      size="lg"
    >
      {modalContent}
    </Modal>
  );
};
