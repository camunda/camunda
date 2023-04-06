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
    return mergeRegister(
      editor.registerCommand(
        SELECTION_CHANGE_COMMAND,
        () => {
          updateToolbar();
          return false;
        },
        COMMAND_PRIORITY_CRITICAL
      ),
      editor.registerEditableListener((editable) => {
        setIsEditable(editable);
      }),
      editor.registerUpdateListener(({editorState}) => {
        editorState.read(updateToolbar);
      })
    );
  }, [editor, updateToolbar]);

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
