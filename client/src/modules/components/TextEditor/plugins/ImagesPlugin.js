/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect, useState} from 'react';
import {
  $createParagraphNode,
  $insertNodes,
  $isRootOrShadowRoot,
  COMMAND_PRIORITY_EDITOR,
  createCommand,
} from 'lexical';
import {$wrapNodeInElement, mergeRegister} from '@lexical/utils';
import {useLexicalComposerContext} from '@lexical/react/LexicalComposerContext';

import {Button, Form, Input, Labeled, Modal} from 'components';
import {t} from 'translation';

import {validateUrl} from './service';

import {$createImageNode, ImageNode} from '../nodes';

export const INSERT_IMAGE_COMMAND = createCommand('INSERT_IMAGE_COMMAND');

export function InsertImageModal({editor, onClose}) {
  const [src, setSrc] = useState('');
  const [altText, setAltText] = useState('');

  const onClick = () => {
    editor.dispatchCommand(INSERT_IMAGE_COMMAND, {src, altText: altText || src});
    onClose();
  };

  return (
    <Modal open onClose={onClose}>
      <Modal.Header>{t('textEditor.plugins.images.title')}</Modal.Header>
      <Modal.Content>
        <Form>
          <Form.Group>
            <Labeled label={t('textEditor.plugins.images.urlLabel')}>
              <Input
                placeholder={t('textEditor.plugins.images.urlPlaceholder')}
                onChange={({target: {value}}) => setSrc(value)}
                value={src}
              />
            </Labeled>
            <Labeled label={t('textEditor.plugins.images.altTextLabel')}>
              <Input
                placeholder={t('textEditor.plugins.images.altTextPlaceholder')}
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
        <Button main primary disabled={!validateUrl(src)} onClick={onClick}>
          {t('common.add')}
        </Button>
      </Modal.Actions>
    </Modal>
  );
}

export default function ImagesPlugin() {
  const [editor] = useLexicalComposerContext();

  useEffect(() => {
    if (!editor.hasNodes([ImageNode])) {
      throw new Error('ImagesPlugin: ImageNode not registered on editor');
    }

    return mergeRegister(
      editor.registerCommand(
        INSERT_IMAGE_COMMAND,
        (payload) => {
          const imageNode = $createImageNode(payload);
          $insertNodes([imageNode]);
          if ($isRootOrShadowRoot(imageNode.getParentOrThrow())) {
            $wrapNodeInElement(imageNode, $createParagraphNode).selectEnd();
          }

          return true;
        },
        COMMAND_PRIORITY_EDITOR
      )
    );
  }, [editor]);

  return null;
}
