/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect, useState} from 'react';
import {
  $getSelection,
  $isRangeSelection,
  COMMAND_PRIORITY_CRITICAL,
  LexicalEditor,
  SELECTION_CHANGE_COMMAND,
} from 'lexical';
import {$patchStyleText, $getSelectionStyleValueForProperty} from '@lexical/selection';
import {mergeRegister} from '@lexical/utils';
import {MenuButton, MenuItemSelectable} from '@carbon/react';

export const FONT_SIZES = [
  '10px',
  '11px',
  '12px',
  '13px',
  '14px',
  '15px',
  '16px',
  '17px',
  '18px',
  '19px',
  '20px',
] as const;

export default function FontSizeOptions({
  editor,
  disabled = false,
}: {
  editor: LexicalEditor;
  disabled?: boolean;
}) {
  const [fontSize, setFontSize] = useState('16px');

  const updateFontSize = useCallback(() => {
    const selection = $getSelection();
    if ($isRangeSelection(selection)) {
      setFontSize($getSelectionStyleValueForProperty(selection, 'font-size', '16px'));
    }
  }, []);

  useEffect(() => {
    return mergeRegister(
      editor.registerCommand(
        SELECTION_CHANGE_COMMAND,
        () => {
          updateFontSize();
          return false;
        },
        COMMAND_PRIORITY_CRITICAL
      ),
      editor.registerUpdateListener(({editorState}) => {
        editorState.read(updateFontSize);
      })
    );
  }, [editor, updateFontSize]);

  const handleClick = (option: string) => {
    editor.update(() => {
      const selection = $getSelection();
      if ($isRangeSelection(selection)) {
        $patchStyleText(selection, {
          'font-size': option,
        });
      }
    });
  };

  return (
    <MenuButton
      size="sm"
      kind="ghost"
      disabled={disabled}
      label={fontSize}
      className="FontSizeOptions"
      menuAlignment="bottom-start"
    >
      {FONT_SIZES.map((size) => (
        <MenuItemSelectable
          selected={fontSize === size}
          onChange={() => handleClick(size)}
          key={size}
          label={size}
        />
      ))}
    </MenuButton>
  );
}
