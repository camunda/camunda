/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Button} from '@carbon/react';
import {Maximize} from '@carbon/react/icons';
import {CopyButton} from 'modules/components/CopyButton';
import {Container, Preview, Actions} from './styled';
import {RichTextEditorModal} from 'modules/components/RichTextEditorModal';

const titleByActor: Record<ConversationMessageProps['actor'], string> = {
  system: 'System prompt',
  user: 'User message',
  assistant: 'Assistant message',
};

type ConversationMessageProps = {
  actor: 'user' | 'assistant' | 'system';
  content: string;
};

const ConversationMessage: React.FC<ConversationMessageProps> = ({
  actor,
  content,
}) => {
  const [isModalOpen, setIsModalOpen] = useState(false);

  return (
    <Container>
      <Preview>{content}</Preview>
      <Actions>
        <CopyButton value={content} />
        <Button
          kind="ghost"
          size="sm"
          renderIcon={Maximize}
          iconDescription="Expand"
          onClick={() => setIsModalOpen(true)}
        >
          Expand
        </Button>
      </Actions>
      <RichTextEditorModal
        title={titleByActor[actor]}
        language="markdown"
        readOnly
        value={content}
        isVisible={isModalOpen}
        onClose={() => setIsModalOpen(false)}
      />
    </Container>
  );
};

export {ConversationMessage};
