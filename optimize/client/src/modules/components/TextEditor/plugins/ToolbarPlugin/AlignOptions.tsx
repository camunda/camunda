/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MenuButton, MenuItem} from '@carbon/react';
import {ElementFormatType, FORMAT_ELEMENT_COMMAND, LexicalEditor} from 'lexical';

import {t} from 'translation';

export default function AlignOptions({
  disabled,
  editor,
}: {
  disabled?: boolean;
  editor: LexicalEditor;
}) {
  const onAlign = (alignment: ElementFormatType) => () => {
    editor.dispatchCommand(FORMAT_ELEMENT_COMMAND, alignment);
  };

  const ALIGN_TYPES: ElementFormatType[] = ['left', 'center', 'right'];

  return (
    <MenuButton
      disabled={disabled}
      label={t('textEditor.toolbar.align.label').toString()}
      className="AlignOptions"
      kind="ghost"
      size="sm"
    >
      {ALIGN_TYPES.map((type) => (
        <MenuItem
          key={type}
          onClick={onAlign(type)}
          label={t(`textEditor.toolbar.align.${type}`).toString()}
        />
      ))}
    </MenuButton>
  );
}
