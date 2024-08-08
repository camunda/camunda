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
  $isRootOrShadowRoot,
  COMMAND_PRIORITY_CRITICAL,
  SELECTION_CHANGE_COMMAND,
} from 'lexical';
import {useLexicalComposerContext} from '@lexical/react/LexicalComposerContext';
import {$findMatchingParent, mergeRegister} from '@lexical/utils';

import BlockTypeDropdown from './BlockTypeOptions';
import FontSizeOptions from './FontSizeOptions';
import InlineStylesButtons from './InlineStylesButtons';
import InsertOptions from './InsertOptions';
import AlignOptions from './AlignOptions';
import useModal from './useModal';
import {getNodeType} from './service';

export default function ToolbarPlugin() {
  const [editor] = useLexicalComposerContext();
  const [activeEditor, setActiveEditor] = useState(editor);
  const [blockType, setBlockType] = useState('paragraph');
  const [isEditable, setIsEditable] = useState(() => editor.isEditable());
  const [modal, showModal] = useModal();

  const updateToolbar = useCallback(() => {
    const selection = $getSelection();
    if ($isRangeSelection(selection)) {
      const anchorNode = selection.anchor.getNode();
      let element =
        anchorNode.getKey() === 'root'
          ? anchorNode
          : $findMatchingParent(anchorNode, (e) => {
              const parent = e.getParent();
              return parent !== null && $isRootOrShadowRoot(parent);
            });

      if (element === null) {
        element = anchorNode.getTopLevelElementOrThrow();
      }

      const type = getNodeType(element, anchorNode);
      setBlockType(type);
    }
  }, []);

  useEffect(() => {
    return editor.registerCommand(
      SELECTION_CHANGE_COMMAND,
      (_payload, newEditor) => {
        updateToolbar();
        setActiveEditor(newEditor);
        return false;
      },
      COMMAND_PRIORITY_CRITICAL
    );
  }, [editor, updateToolbar]);

  useEffect(() => {
    return mergeRegister(
      editor.registerEditableListener((editable) => {
        setIsEditable(editable);
      }),
      activeEditor.registerUpdateListener(({editorState}) => {
        editorState.read(() => {
          updateToolbar();
        });
      })
    );
  }, [updateToolbar, activeEditor, editor]);

  return (
    <div className="toolbar">
      <BlockTypeDropdown editor={editor} disabled={!isEditable} blockType={blockType} />
      <FontSizeOptions editor={editor} disabled={!isEditable} />
      <InlineStylesButtons editor={editor} disabled={!isEditable} />
      <InsertOptions editor={editor} disabled={!isEditable} showModal={showModal} />
      <AlignOptions editor={editor} disabled={!isEditable} />
      {modal}
    </div>
  );
}
