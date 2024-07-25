/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import classnames from 'classnames';
import {SerializedEditorState, SerializedLexicalNode} from 'lexical';
import {InitialConfigType, LexicalComposer} from '@lexical/react/LexicalComposer';
import {TextArea} from '@carbon/react';

import {isTextTileTooLong, TEXT_REPORT_MAX_CHARACTERS} from 'services';

import editorNodes from './nodes';
import Editor from './Editor';

import './TextEditor.scss';

function onError(error: Error) {
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

type SimpleEditorProps = {
  simpleEditor: true;
  onChange: (text: string) => void;
  initialValue?: string | null;
};

type RichEditorProps = {
  simpleEditor?: never;
  onChange?: (value: SerializedEditorState) => void;
  initialValue?: SerializedEditorState | null;
};

type CommonProps = {
  label: string;
  hideLabel?: boolean;
  limit?: number;
};

type TextEditorProps = CommonProps & (SimpleEditorProps | RichEditorProps);

export default function TextEditor({
  label,
  hideLabel,
  onChange,
  simpleEditor,
  initialValue,
  limit = TEXT_REPORT_MAX_CHARACTERS,
}: TextEditorProps) {
  const initialConfig: InitialConfigType = {
    editorState: initialValue ? JSON.stringify(initialValue) : undefined,
    editable: !!onChange,
    namespace: 'Editor',
    nodes: editorNodes,
    theme,
    onError,
  };
  const [isError, setIsError] = useState(
    !simpleEditor && isTextTileTooLong(TextEditor.getEditorStateLength(initialValue))
  );

  return (
    <div
      onKeyDown={(e) => {
        e.stopPropagation();
      }}
      className="TextEditor"
    >
      {!hideLabel && <label className="cds--label">{label}</label>}
      {simpleEditor ? (
        <TextArea
          aria-label={label}
          labelText={label}
          hideLabel
          value={initialValue || undefined}
          className="SimpleEditor"
          invalid={isTextTileTooLong(initialValue?.length || 0, limit)}
          onChange={(evt) => onChange(evt.target.value)}
          data-modal-primary-focus
        />
      ) : (
        <LexicalComposer initialConfig={initialConfig}>
          <Editor
            label={label}
            onChange={(sanitizedEditorState) => {
              setIsError(
                isTextTileTooLong(TextEditor.getEditorStateLength(sanitizedEditorState), limit)
              );
              onChange?.(sanitizedEditorState);
            }}
            showToolbar={!!onChange}
            error={isError}
          />
        </LexicalComposer>
      )}
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

TextEditor.getEditorStateLength = function (
  editorState: SerializedEditorState | string | null | undefined
): number {
  if (typeof editorState === 'string' || editorState === null) {
    return editorState?.length || 0;
  }

  let length = 0;

  if (editorState?.root?.children) {
    length += reduceChildren(editorState.root.children);
  }

  return length;
};

TextEditor.CharCount = function ({
  editorState,
  limit = TEXT_REPORT_MAX_CHARACTERS,
}: {
  editorState: SerializedEditorState | string | null | undefined;
  limit?: number;
}) {
  const textLength = TextEditor.getEditorStateLength(editorState);

  return (
    <div
      className={classnames('TextEditor', 'CharCount', {
        error: isTextTileTooLong(textLength, limit),
      })}
    >
      {textLength}/{limit}
    </div>
  );
};
