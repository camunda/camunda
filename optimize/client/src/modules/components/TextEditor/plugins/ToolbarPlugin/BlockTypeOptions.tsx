/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MenuButton, MenuItemSelectable} from '@carbon/react';
import {$createParagraphNode, $getSelection, LexicalEditor} from 'lexical';
import {
  INSERT_ORDERED_LIST_COMMAND,
  INSERT_UNORDERED_LIST_COMMAND,
  REMOVE_LIST_COMMAND,
} from '@lexical/list';
import {$createHeadingNode, HeadingTagType} from '@lexical/rich-text';
import {$setBlocksType} from '@lexical/selection';

import {t} from 'translation';

export const BLOCK_TYPES = ['paragraph', 'h1', 'h2', 'h3', 'number', 'bullet'] as const;

export default function BlockTypeDropdown({
  editor,
  blockType,
  disabled = false,
}: {
  editor: LexicalEditor;
  blockType: string;
  disabled?: boolean;
}) {
  const formatParagraph = () => {
    editor.update(() => {
      const selection = $getSelection();
      $setBlocksType(selection, () => $createParagraphNode());
    });
  };

  const formatHeading = (headingSize: HeadingTagType) => () => {
    if (blockType !== headingSize) {
      editor.update(() => {
        const selection = $getSelection();
        $setBlocksType(selection, () => $createHeadingNode(headingSize));
      });
    }
  };

  const formatBulletList = () => {
    if (blockType !== 'bullet') {
      editor.dispatchCommand(INSERT_UNORDERED_LIST_COMMAND, undefined);
    } else {
      editor.dispatchCommand(REMOVE_LIST_COMMAND, undefined);
    }
  };

  const formatNumberedList = () => {
    if (blockType !== 'number') {
      editor.dispatchCommand(INSERT_ORDERED_LIST_COMMAND, undefined);
    } else {
      editor.dispatchCommand(REMOVE_LIST_COMMAND, undefined);
    }
  };

  const FORMATTERS = {
    bullet: formatBulletList,
    h1: formatHeading('h1'),
    h2: formatHeading('h2'),
    h3: formatHeading('h3'),
    number: formatNumberedList,
    paragraph: formatParagraph,
  };

  if (!BLOCK_TYPES.some((type) => type === blockType)) {
    return null;
  }

  return (
    <MenuButton
      disabled={disabled}
      label={t(`textEditor.toolbar.blockStyles.${blockType}`).toString()}
      className="BlockTypeOptions"
      size="sm"
      kind="ghost"
      menuAlignment="bottom-start"
    >
      {BLOCK_TYPES.map((key) => {
        return (
          <MenuItemSelectable
            key={key}
            selected={blockType === key}
            onChange={FORMATTERS[key]}
            label={t(`textEditor.toolbar.blockStyles.${key}`).toString()}
          />
        );
      })}
    </MenuButton>
  );
}
