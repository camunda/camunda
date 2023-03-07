/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useCallback} from 'react';
import classnames from 'classnames';
import {RichTextPlugin} from '@lexical/react/LexicalRichTextPlugin';
import {ContentEditable} from '@lexical/react/LexicalContentEditable';
import LexicalErrorBoundary from '@lexical/react/LexicalErrorBoundary';
import {OnChangePlugin} from '@lexical/react/LexicalOnChangePlugin';
import update from 'immutability-helper';

import editorPlugins, {ToolbarPlugin} from './plugins';

import './Editor.scss';

export default function Editor({onChange, error}) {
  const contentEditable = <ContentEditable className={classnames('editor', {error})} />;

  const onEditorChange = useCallback(
    (editorState) => {
      onChange?.(sanitizeEditorState(editorState.toJSON()));
    },
    [onChange]
  );

  return (
    <div className="Editor">
      {onChange && <ToolbarPlugin />}
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

function trimChildrenEmptyParagraphs(children = []) {
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

function isEmptyParagraph(child) {
  return child?.type === 'paragraph' && !child?.children.length;
}

function sanitizeEditorState(editorState) {
  if (!editorState?.root?.children.length) {
    return editorState;
  }

  return update(editorState, {
    root: {children: {$set: trimChildrenEmptyParagraphs(editorState.root.children)}},
  });
}
