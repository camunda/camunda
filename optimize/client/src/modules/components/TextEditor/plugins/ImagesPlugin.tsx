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
  $createParagraphNode,
  $insertNodes,
  $isRootOrShadowRoot,
  COMMAND_PRIORITY_EDITOR,
  createCommand,
  LexicalEditor,
} from 'lexical';
import {$wrapNodeInElement, mergeRegister} from '@lexical/utils';
import {useLexicalComposerContext} from '@lexical/react/LexicalComposerContext';

import {Modal} from 'components';
import {t} from 'translation';

import {validateUrl} from './service';

import {$createImageNode, ImageNode} from '../nodes';

type ImagePayload = {altText: string; src: string};

export const INSERT_IMAGE_COMMAND = createCommand<ImagePayload>('INSERT_IMAGE_COMMAND');

export function InsertImageModal({editor, onClose}: {editor: LexicalEditor; onClose?: () => void}) {
  const [src, setSrc] = useState('');
  const [altText, setAltText] = useState<string | undefined>('');

  const onClick = () => {
    editor.dispatchCommand(INSERT_IMAGE_COMMAND, {src, altText: altText || src});
    onClose?.();
  };

  return (
    <Modal open onClose={onClose} className="InsertModal">
      <Modal.Header title={t('textEditor.plugins.images.title')} />
      <Modal.Content>
        <Form>
          <Stack gap={6}>
            <TextInput
              id="imageUrl"
              labelText={t('textEditor.plugins.images.urlLabel')}
              placeholder={t('textEditor.plugins.images.urlPlaceholder').toString()}
              onChange={({target: {value}}) => setSrc(value)}
              value={src}
              data-modal-primary-focus
            />
            <TextInput
              id="imageAltText"
              labelText={t('textEditor.plugins.images.altTextLabel')}
              placeholder={t('textEditor.plugins.images.altTextPlaceholder').toString()}
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
        <Button disabled={!validateUrl(src)} onClick={onClick}>
          {t('common.add')}
        </Button>
      </Modal.Footer>
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
