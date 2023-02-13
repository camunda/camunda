/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useCallback, useEffect, useState} from 'react';
import {
  $getSelection,
  $isRangeSelection,
  COMMAND_PRIORITY_CRITICAL,
  SELECTION_CHANGE_COMMAND,
} from 'lexical';
import {$patchStyleText, $getSelectionStyleValueForProperty} from '@lexical/selection';
import {mergeRegister} from '@lexical/utils';

import {Dropdown} from 'components';

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
];

export default function FontSizeOptions({editor, disabled = false}) {
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

  const handleClick = (option) => {
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
    <Dropdown small disabled={disabled} label={fontSize} className="FontSizeOptions">
      {FONT_SIZES.map((size) => (
        <Dropdown.Option active={fontSize === size} onClick={() => handleClick(size)} key={size}>
          {size}
        </Dropdown.Option>
      ))}
    </Dropdown>
  );
}
