/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import classnames from 'classnames';
import {LexicalComposer} from '@lexical/react/LexicalComposer';

import editorNodes from './nodes';
import Editor from './Editor';

import './TextEditor.scss';

function onError(error) {
  console.error(error);
}

const theme = {
  paragraph: 'p',
  text: {
    code: 'code',
    bold: 'bold',
    italic: 'italic',
    strikethrough: 'strikethrough',
    underline: 'underline',
  },
  heading: {
    h1: 'h1',
    h2: 'h2',
    h3: 'h3',
  },
};

export default function TextEditor({onChange, error = false, initialValue}) {
  const initialConfig = {
    editorState: initialValue ? JSON.stringify(initialValue) : null,
    editable: !!onChange,
    namespace: 'Editor',
    nodes: editorNodes,
    theme,
    onError,
  };

  return (
    <div
      onKeyDown={(e) => {
        e.stopPropagation();
      }}
      className="TextEditor"
    >
      <LexicalComposer initialConfig={initialConfig}>
        <Editor onChange={onChange} error={error} />
      </LexicalComposer>
    </div>
  );
}

function reduceChildren(children, initial = 0) {
  return children.reduce((sum, curr) => {
    sum += curr?.text?.length || 0 + curr?.url?.length || 0 + curr?.src?.length || 0;

    if (curr?.children) {
      return reduceChildren(curr.children, sum);
    }
    return sum;
  }, initial);
}

TextEditor.getEditorStateLength = function (editorState) {
  let length = 0;

  if (editorState?.root?.children) {
    length += reduceChildren(editorState.root.children);
  }

  return length;
};

TextEditor.CharCount = function ({editorState, limit}) {
  const textLenght = TextEditor.getEditorStateLength(editorState);
  return (
    <div className={classnames('TextEditor', 'CharCount', {error: textLenght > limit})}>
      {textLenght}/{limit}
    </div>
  );
};
