/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useState} from 'react';
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

import {Button, Form, Input, Labeled, Modal} from 'components';
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
      <Modal.Header>{t('textEditor.plugins.link.title')}</Modal.Header>
      <Modal.Content>
        <Form>
          <Form.Group>
            <Labeled label={t('textEditor.plugins.link.urlLabel')}>
              <Input
                placeholder={t('textEditor.plugins.link.urlPlaceholder')}
                onChange={({target: {value}}) => setUrl(value)}
                value={url}
              />
            </Labeled>
          </Form.Group>
          <Form.Group>
            <Labeled label={t('textEditor.plugins.link.altTextLabel')}>
              <Input
                placeholder={t('textEditor.plugins.link.altTextPlaceholder')}
                onChange={({target: {value}}) => setAltText(value)}
                value={altText}
              />
            </Labeled>
          </Form.Group>
        </Form>
      </Modal.Content>
      <Modal.Actions>
        <Button main onClick={onClose}>
          {t('common.cancel')}
        </Button>
        <Button main primary disabled={!validateUrl(url)} onClick={onClick}>
          {t('common.add')}
        </Button>
      </Modal.Actions>
    </Modal>
  );
}
