/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {Button, Form, Stack, TextInput} from '@carbon/react';
import {
  createCommand,
  $createTextNode,
  COMMAND_PRIORITY_EDITOR,
  $getSelection,
  $isRangeSelection,
  $createParagraphNode,
  LexicalEditor,
} from 'lexical';
import {$createLinkNode, LinkNode} from '@lexical/link';
import {mergeRegister, $insertNodeToNearestRoot} from '@lexical/utils';
import {useLexicalComposerContext} from '@lexical/react/LexicalComposerContext';
import {LinkPlugin as LexicalLinkPlugin} from '@lexical/react/LexicalLinkPlugin';

import {Modal} from 'components';
import {t} from 'translation';

import {validateUrl} from './service';

type LinkPayload = {
  altText: string;
  url: string;
};

export default function LinkPlugin() {
  const [editor] = useLexicalComposerContext();

  useEffect(() => {
    if (!editor.hasNodes([LinkNode])) {
      throw new Error('ImagesPlugin: ImageNode not registered on editor');
    }

    return mergeRegister(
      editor.registerCommand(
        INSERT_LINK_COMMAND,
        (payload) => {
          const selection = $getSelection();

          if (!$isRangeSelection(selection)) {
            return false;
          }

          const linkNode = $createLinkNode(payload.url, {
            target: '_blank',
            rel: 'noopener norefereer',
          }).append($createTextNode(payload.altText));

          const focusNode = selection.focus.getNode();

          if (focusNode !== null) {
            $insertNodeToNearestRoot($createParagraphNode().append(linkNode));
          }

          return true;
        },
        COMMAND_PRIORITY_EDITOR
      )
    );
  }, [editor]);
  return <LexicalLinkPlugin validateUrl={validateUrl} />;
}

export const INSERT_LINK_COMMAND = createCommand<LinkPayload>('INSERT_LINK_COMMAND');

export function InsertLinkModal({editor, onClose}: {editor: LexicalEditor; onClose?: () => void}) {
  const [url, setUrl] = useState('');
  const [altText, setAltText] = useState('');

  const onClick = () => {
    editor.dispatchCommand(INSERT_LINK_COMMAND, {url, altText: altText || url});
    onClose?.();
  };

  return (
    <Modal open onClose={onClose} className="InsertModal">
      <Modal.Header title={t('textEditor.plugins.link.title')} />
      <Modal.Content>
        <Form>
          <Stack gap={6}>
            <TextInput
              id="linkUrl"
              labelText={t('textEditor.plugins.link.urlLabel')}
              placeholder={t('textEditor.plugins.link.urlPlaceholder').toString()}
              onChange={({target: {value}}) => setUrl(value)}
              value={url}
              data-modal-primary-focus
            />
            <TextInput
              id="linkAltText"
              labelText={t('textEditor.plugins.link.altTextLabel')}
              placeholder={t('textEditor.plugins.link.altTextPlaceholder').toString()}
              onChange={({target: {value}}) => setAltText(value)}
              value={altText}
            />
          </Stack>
        </Form>
      </Modal.Content>
      <Modal.Footer>
        <Button kind="secondary" onClick={onClose}>
          {t('common.cancel')}
        </Button>
        <Button disabled={!validateUrl(url)} onClick={onClick}>
          {t('common.add')}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}
