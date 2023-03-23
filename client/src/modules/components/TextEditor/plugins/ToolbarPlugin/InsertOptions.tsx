/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {INSERT_HORIZONTAL_RULE_COMMAND} from '@lexical/react/LexicalHorizontalRuleNode';

import {Dropdown} from 'components';
import {LexicalEditor} from 'lexical';
import {t} from 'translation';

import {InsertImageModal} from '../ImagesPlugin';
import {InsertLinkModal} from '../LinkPlugin';

export default function InsertOptions({
  editor,
  disabled,
  showModal,
}: {
  editor: LexicalEditor;
  disabled?: boolean;
  showModal: (getModal: (onClose: () => void) => JSX.Element) => void;
}) {
  return (
    <Dropdown
      small
      disabled={disabled}
      label={t('textEditor.toolbar.insert.label')}
      className="InsertOptions"
    >
      <Dropdown.Option
        onClick={() => {
          editor.dispatchCommand(INSERT_HORIZONTAL_RULE_COMMAND, undefined);
        }}
      >
        {t('textEditor.toolbar.insert.horizontalRule')}
      </Dropdown.Option>
      <Dropdown.Option
        onClick={() => {
          showModal((onClose) => <InsertImageModal editor={editor} onClose={onClose} />);
        }}
      >
        {t('textEditor.toolbar.insert.image')}
      </Dropdown.Option>
      <Dropdown.Option
        onClick={() => {
          showModal((onClose) => <InsertLinkModal editor={editor} onClose={onClose} />);
        }}
      >
        {t('textEditor.toolbar.insert.link')}
      </Dropdown.Option>
    </Dropdown>
  );
}
