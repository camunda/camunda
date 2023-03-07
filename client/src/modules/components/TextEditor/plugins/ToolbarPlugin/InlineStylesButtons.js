/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useCallback, useEffect, useState} from 'react';
import {
  $getSelection,
  $isRangeSelection,
  COMMAND_PRIORITY_CRITICAL,
  FORMAT_TEXT_COMMAND,
  SELECTION_CHANGE_COMMAND,
} from 'lexical';
import {TextBold, TextItalic, TextStrikethrough, TextUnderline} from '@carbon/icons-react';
import {mergeRegister} from '@lexical/utils';

import {Button} from 'components';
import {t} from 'translation';

export default function InlineStylesButtons({disabled, editor}) {
  const [isBold, setIsBold] = useState(false);
  const [isItalic, setIsItalic] = useState(false);
  const [isUnderline, setIsUnderline] = useState(false);
  const [isStrikethrough, setIsStrikethrough] = useState(false);

  const updateStyles = useCallback(() => {
    const selection = $getSelection();
    if ($isRangeSelection(selection)) {
      setIsBold(selection.hasFormat('bold'));
      setIsItalic(selection.hasFormat('italic'));
      setIsUnderline(selection.hasFormat('underline'));
      setIsStrikethrough(selection.hasFormat('strikethrough'));
    }
  }, []);

  useEffect(() => {
    return mergeRegister(
      editor.registerCommand(
        SELECTION_CHANGE_COMMAND,
        () => {
          updateStyles();
          return false;
        },
        COMMAND_PRIORITY_CRITICAL
      ),
      editor.registerUpdateListener(({editorState}) => {
        editorState.read(updateStyles);
      })
    );
  }, [editor, updateStyles]);

  const BUTTONS = {
    bold: {Icon: TextBold, active: isBold},
    italic: {Icon: TextItalic, active: isItalic},
    underline: {Icon: TextUnderline, active: isUnderline},
    strikethrough: {Icon: TextStrikethrough, active: isStrikethrough},
  };

  return (
    <>
      {Object.entries(BUTTONS).map(([key, {Icon, active}]) => (
        <Button
          key={key}
          small
          title={t(`textEditor.toolbar.styles.${key}`)}
          disabled={disabled}
          active={active}
          onClick={() => {
            editor.dispatchCommand(FORMAT_TEXT_COMMAND, key);
          }}
        >
          <Icon />
        </Button>
      ))}
    </>
  );
}
