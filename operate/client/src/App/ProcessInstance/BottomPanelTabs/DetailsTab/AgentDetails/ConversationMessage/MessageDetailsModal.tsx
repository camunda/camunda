/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, type ReactNode} from 'react';
import {Modal} from '@carbon/react';
import {CopyButton} from 'modules/components/CopyButton';
import {StructuredText} from './StructuredValue/StructuredText';
import {StructuredValue} from './StructuredValue';
import {
  ContentsLayout,
  ContentsPane,
  ModalContent,
  ModalToolbar,
  ModalAttachments,
} from './styled';

import {
  ModalViewSwitcher,
  SourceEditor,
  type ModalView,
  type ModalViewProps,
} from './ModalView';

type MessageDetailsModalProps = ModalViewProps & {
  title: string;
  language: 'markdown' | 'structured';
  /** Markdown source, or the copy value for structured content. */
  content: string;
  value?: unknown;
  onClose: () => void;
  navigation?: ReactNode;
  contents?: ReactNode;
  attachments?: ReactNode;
};

const MessageDetailsModal: React.FC<MessageDetailsModalProps> = ({
  title,
  language,
  content,
  value,
  onClose,
  navigation,
  contents,
  attachments,
  view,
  onViewChange,
}) => {
  const [localView, setLocalView] = useState<ModalView>('preview');
  const modalView = view ?? localView;
  const setModalView = onViewChange ?? setLocalView;

  const body = (
    <>
      {language === 'markdown' ? (
        <>
          <ModalToolbar>
            <ModalViewSwitcher view={modalView} onChange={setModalView} />
            <CopyButton value={content} />
          </ModalToolbar>
          {modalView === 'preview' ? (
            <ModalContent className="agent-message-content">
              <StructuredText content={content} />
            </ModalContent>
          ) : (
            <SourceEditor value={content} language="markdown" />
          )}
          {modalView === 'preview' && attachments && (
            <ModalAttachments>{attachments}</ModalAttachments>
          )}
        </>
      ) : (
        <>
          <ModalToolbar>
            <span />
            <CopyButton value={content} />
          </ModalToolbar>
          <ModalContent>
            <StructuredValue value={value} />
          </ModalContent>
        </>
      )}
    </>
  );

  return (
    <Modal
      open
      modalHeading={
        navigation ? (
          <span className="agent-history-heading">
            <span>{title}</span>
            {navigation}
          </span>
        ) : (
          title
        )
      }
      onRequestClose={onClose}
      size="lg"
      passiveModal
      className={navigation || contents ? 'agent-history-modal' : undefined}
    >
      {contents ? (
        <ContentsLayout>
          {contents}
          <ContentsPane>{body}</ContentsPane>
        </ContentsLayout>
      ) : (
        body
      )}
    </Modal>
  );
};

export {MessageDetailsModal};
