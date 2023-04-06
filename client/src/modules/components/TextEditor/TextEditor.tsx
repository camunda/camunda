/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import classnames from 'classnames';
import {SerializedEditorState, SerializedLexicalNode} from 'lexical';
import {InitialConfigType, LexicalComposer} from '@lexical/react/LexicalComposer';

import {isTextReportTooLong, TEXT_REPORT_MAX_CHARACTERS} from 'services';

import editorNodes from './nodes';
import Editor from './Editor';

import './TextEditor.scss';

function onError(error: Error) {
  console.error(error);
}

const emptyState: SerializedEditorState = {
  root: {
    children: [
      {
        type: 'paragraph',
        version: 1,
      },
    ],
    direction: null,
    format: '',
    indent: 0,
    type: 'root',
    version: 1,
  },
};

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

export default function TextEditor({
  onChange,
  initialValue = emptyState,
}: {
  onChange?: (value: SerializedEditorState) => void;
  initialValue?: SerializedEditorState;
}) {
  const initialConfig: InitialConfigType = {
    editorState: JSON.stringify(initialValue),
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
        <Editor
          onChange={onChange}
          error={isTextReportTooLong(TextEditor.getEditorStateLength(initialValue))}
        />
      </LexicalComposer>
    </div>
  );
}

interface SerializedNode extends SerializedLexicalNode {
  text?: string;
  url?: string;
  src?: string;
  children?: SerializedNode[];
}

function reduceChildren(children: SerializedNode[], initial = 0): number {
  return children.reduce<number>((sum, curr) => {
    sum += (curr?.text?.length || 0) + (curr?.url?.length || 0) + (curr?.src?.length || 0);

    if (curr?.children) {
      return reduceChildren(curr.children, sum);
    }
    return sum;
  }, initial);
}

TextEditor.getEditorStateLength = function (editorState: SerializedEditorState): number {
  let length = 0;

  if (editorState?.root?.children) {
    length += reduceChildren(editorState.root.children);
  }

  return length;
};

TextEditor.CharCount = function ({editorState}: {editorState: SerializedEditorState}) {
  const textLenght = TextEditor.getEditorStateLength(editorState);
  return (
    <div
      className={classnames('TextEditor', 'CharCount', {
        error: isTextReportTooLong(textLenght),
      })}
    >
      {textLenght}/{TEXT_REPORT_MAX_CHARACTERS}
    </div>
  );
};
