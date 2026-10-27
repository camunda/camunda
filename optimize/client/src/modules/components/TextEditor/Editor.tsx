/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback} from 'react';
import classnames from 'classnames';
import {EditorState, SerializedEditorState, SerializedLexicalNode} from 'lexical';
import {RichTextPlugin} from '@lexical/react/LexicalRichTextPlugin';
import {ContentEditable} from '@lexical/react/LexicalContentEditable';
import {LexicalErrorBoundary} from '@lexical/react/LexicalErrorBoundary';
import {OnChangePlugin} from '@lexical/react/LexicalOnChangePlugin';
import update from 'immutability-helper';

import editorPlugins, {ToolbarPlugin} from './plugins';

import './Editor.scss';

export default function Editor({
  label,
  onChange,
  error,
  showToolbar,
}: {
  label: string;
  onChange?: (value: SerializedEditorState) => void;
  error?: boolean;
  showToolbar?: boolean;
}) {
  const contentEditable = (
    <ContentEditable
      ariaLabel={label}
      className={classnames('editor', 'cds--text-area', {error})}
    />
  );

  const onEditorChange = useCallback(
    (editorState: EditorState) => {
      onChange?.(sanitizeEditorState(editorState.toJSON()));
    },
    [onChange]
  );

  return (
    <div className="Editor">
      {showToolbar && <ToolbarPlugin />}
      <RichTextPlugin
        contentEditable={contentEditable}
        placeholder={null}
        ErrorBoundary={LexicalErrorBoundary}
      />
      <OnChangePlugin onChange={onEditorChange} ignoreSelectionChange />
      {editorPlugins.map((EditorPlugin, idx) => (
        <EditorPlugin key={idx} />
      ))}
    </div>
  );
}

interface SerializedNode extends SerializedLexicalNode {
  children?: SerializedNode[];
}

function trimChildrenEmptyParagraphs(children: SerializedNode[] = []) {
  let newChildren = [...children];

  while (isEmptyParagraph(newChildren[newChildren.length - 1])) {
    newChildren.pop();
  }

  while (isEmptyParagraph(newChildren[0])) {
    newChildren.shift();
  }

  newChildren = newChildren.map((child) => {
    if (child?.children?.length) {
      child.children = [...trimChildrenEmptyParagraphs(child.children)];
    }
    return child;
  });

  return newChildren;
}

function isEmptyParagraph(child?: SerializedNode) {
  return child?.type === 'paragraph' && !child?.children?.length;
}

function sanitizeEditorState(editorState: SerializedEditorState) {
  if (!editorState?.root?.children.length) {
    return editorState;
  }

  return update(editorState, {
    root: {children: {$set: trimChildrenEmptyParagraphs(editorState.root.children)}},
  });
}
