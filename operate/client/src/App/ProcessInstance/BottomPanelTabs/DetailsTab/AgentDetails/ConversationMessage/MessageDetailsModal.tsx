/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {lazy, Suspense, useState} from 'react';
import {Modal, Switch} from '@carbon/react';
import {CopyButton} from 'modules/components/CopyButton';
import {MarkdownMessage} from './MarkdownMessage';
import {ModalContent, ModalToolbar, ViewSwitcher} from './styled';

const RichTextEditor = lazy(async () => {
  const [{loadMonaco}, {RichTextEditor}] = await Promise.all([
    import('modules/loadMonaco'),
    import('modules/components/RichTextEditor'),
  ]);

  loadMonaco();

  return {default: RichTextEditor};
});

type MessageDetailsModalProps = {
  title: string;
  language: 'markdown' | 'json';
  content: string;
  onClose: () => void;
};

const MessageDetailsModal: React.FC<MessageDetailsModalProps> = ({
  title,
  language,
  content,
  onClose,
}) => {
  const [modalView, setModalView] = useState<'preview' | 'source'>('preview');

  return (
    <Modal
      open
      modalHeading={title}
      onRequestClose={onClose}
      size="lg"
      passiveModal
    >
      {language === 'markdown' ? (
        <>
          <ModalToolbar>
            <ViewSwitcher
              size="sm"
              onChange={({name}) => setModalView(name as 'preview' | 'source')}
              selectedIndex={modalView === 'preview' ? 0 : 1}
            >
              <Switch name="preview" text="Preview" />
              <Switch name="source" text="Source" />
            </ViewSwitcher>
            <CopyButton value={content} />
          </ModalToolbar>
          {modalView === 'preview' ? (
            <ModalContent>
              <MarkdownMessage content={content} />
            </ModalContent>
          ) : (
            <Suspense>
              <RichTextEditor value={content} readOnly language="markdown" />
            </Suspense>
          )}
        </>
      ) : (
        <Suspense>
          <RichTextEditor value={content} readOnly language={language} />
        </Suspense>
      )}
    </Modal>
  );
};

export {MessageDetailsModal};
