/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState} from 'react';
import {Button} from '@carbon/react';
import {SerializedEditorState} from 'lexical';

import {Modal, TextEditor} from 'components';
import {isTextTileValid} from 'services';
import {t} from 'translation';

interface TextTileEditModalProps {
  initialValue: SerializedEditorState | null;
  onClose: () => void;
  onConfirm: (value: SerializedEditorState | null) => void;
}

export default function TextTileEditModal({
  initialValue,
  onClose,
  onConfirm,
}: TextTileEditModalProps): JSX.Element {
  const [text, setText] = useState(initialValue);

  const textLength = TextEditor.getEditorStateLength(text);

  const onUpdate = () => {
    onConfirm(text);
    onClose();
  };

  return (
    <Modal open onClose={onClose}>
      <Modal.Header>{t('common.editName', {name: t('report.textTile')})}</Modal.Header>
      <Modal.Content>
        <TextEditor initialValue={initialValue} onChange={setText} />
        <TextEditor.CharCount editorState={text} />
      </Modal.Content>
      <Modal.Footer>
        <Button kind="secondary" onClick={onClose}>
          {t('common.cancel')}
        </Button>
        <Button onClick={onUpdate} disabled={!isTextTileValid(textLength)}>
          {t('common.save')}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}
