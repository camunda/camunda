/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {INSERT_HORIZONTAL_RULE_COMMAND} from '@lexical/react/LexicalHorizontalRuleNode';

import {LexicalEditor} from 'lexical';
import {t} from 'translation';

import {InsertImageModal} from '../ImagesPlugin';
import {InsertLinkModal} from '../LinkPlugin';
import {MenuButton, MenuItem} from '@carbon/react';

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
    <MenuButton
      kind="ghost"
      size="sm"
      disabled={disabled}
      label={t('textEditor.toolbar.insert.label').toString()}
      className="InsertOptions"
      menuAlignment="bottom-start"
    >
      <MenuItem
        onClick={() => {
          editor.dispatchCommand(INSERT_HORIZONTAL_RULE_COMMAND, undefined);
        }}
        label={t('textEditor.toolbar.insert.horizontalRule').toString()}
      />
      <MenuItem
        onClick={() => {
          showModal((onClose) => <InsertImageModal editor={editor} onClose={onClose} />);
        }}
        label={t('textEditor.toolbar.insert.image').toString()}
      />
      <MenuItem
        onClick={() => {
          showModal((onClose) => <InsertLinkModal editor={editor} onClose={onClose} />);
        }}
        label={t('textEditor.toolbar.insert.link').toString()}
      />
    </MenuButton>
  );
}
