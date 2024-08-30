/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect, useState} from 'react';
import {Button} from '@carbon/react';
import {
  $getSelection,
  $isRangeSelection,
  COMMAND_PRIORITY_CRITICAL,
  FORMAT_TEXT_COMMAND,
  LexicalEditor,
  SELECTION_CHANGE_COMMAND,
  TextFormatType,
} from 'lexical';
import {TextBold, TextItalic, TextStrikethrough, TextUnderline} from '@carbon/icons-react';
import {CarbonIconType} from '@carbon/icons-react/lib/CarbonIcon';
import {mergeRegister} from '@lexical/utils';

import {t} from 'translation';

export default function InlineStylesButtons({
  disabled,
  editor,
}: {
  editor: LexicalEditor;
  disabled?: boolean;
}) {
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

  type ButtonState = {Icon: CarbonIconType; isSelected: boolean};

  const BUTTONS: {[k in TextFormatType]?: ButtonState} = {
    bold: {Icon: TextBold, isSelected: isBold},
    italic: {Icon: TextItalic, isSelected: isItalic},
    underline: {Icon: TextUnderline, isSelected: isUnderline},
    strikethrough: {Icon: TextStrikethrough, isSelected: isStrikethrough},
  };

  return (
    <>
      {(Object.entries(BUTTONS) as [key: TextFormatType, value: ButtonState][]).map(
        ([key, {Icon, isSelected}]) => (
          <Button
            key={key}
            size="sm"
            kind="ghost"
            hasIconOnly
            iconDescription={t(`textEditor.toolbar.styles.${key}`).toString()}
            disabled={disabled}
            isSelected={isSelected}
            onClick={() => {
              editor.dispatchCommand(FORMAT_TEXT_COMMAND, key);
            }}
            renderIcon={Icon}
            tooltipPosition="bottom"
          />
        )
      )}
    </>
  );
}
