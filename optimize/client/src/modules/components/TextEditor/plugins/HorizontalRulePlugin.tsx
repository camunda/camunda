/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {$getSelection, $isRangeSelection, COMMAND_PRIORITY_EDITOR} from 'lexical';
import {$insertNodeToNearestRoot, mergeRegister} from '@lexical/utils';
import {useLexicalComposerContext} from '@lexical/react/LexicalComposerContext';
import {
  $createHorizontalRuleNode,
  HorizontalRuleNode,
  INSERT_HORIZONTAL_RULE_COMMAND,
} from '@lexical/react/LexicalHorizontalRuleNode';

export default function HorizontalRulePlugin() {
  const [editor] = useLexicalComposerContext();

  useEffect(() => {
    if (!editor.hasNodes([HorizontalRuleNode])) {
      throw new Error('HorizontalRulePlugin: HorizontalRuleNode not registered on editor');
    }

    return mergeRegister(
      editor.registerCommand(
        INSERT_HORIZONTAL_RULE_COMMAND,
        () => {
          const selection = $getSelection();

          if (!$isRangeSelection(selection)) {
            return false;
          }

          const focusNode = selection.focus.getNode();

          if (focusNode !== null) {
            const horizontalRuleNode = $createHorizontalRuleNode();
            $insertNodeToNearestRoot(horizontalRuleNode);
          }

          return true;
        },
        COMMAND_PRIORITY_EDITOR
      )
    );
  }, [editor]);

  return null;
}
