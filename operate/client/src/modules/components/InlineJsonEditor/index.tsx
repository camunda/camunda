/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useMemo} from 'react';
import CodeMirror from '@uiw/react-codemirror';
import {autocompletion, completeAnyWord} from '@codemirror/autocomplete';
import {json} from '@codemirror/lang-json';
import {HighlightStyle, syntaxHighlighting} from '@codemirror/language';
import {EditorState} from '@codemirror/state';
import {EditorView, tooltips} from '@codemirror/view';
import {tags} from '@lezer/highlight';
import {observer} from 'mobx-react-lite';
import {
  beautifyJSON,
  beautifyTruncatedJSON,
} from 'modules/utils/editor/beautifyJSON';
import {CompletionStyles, EditorWrapper, WriteModeEditor} from './styled';
import {
  EDITOR_LINE_HEIGHT,
  EDITOR_MIN_HEIGHT,
  EDITOR_MAX_LINES,
} from './constants';
import {ReadOnlyEditor} from './ReadOnlyEditor';

const MAX_COMPLETION_LENGTH = 50_000;
const basicSetup = {
  lineNumbers: false,
  foldGutter: false,
  highlightActiveLine: false,
  highlightActiveLineGutter: false,
  highlightSelectionMatches: false,
};
const baseExtensions = [
  json(),
  autocompletion({
    override: [
      (context) =>
        context.state.doc.length <= MAX_COMPLETION_LENGTH
          ? completeAnyWord(context)
          : null,
    ],
    interactionDelay: 0,
    tooltipClass: () => 'inline-json-completions',
  }),
  tooltips({parent: document.body}),
  syntaxHighlighting(
    HighlightStyle.define([
      {tag: tags.propertyName, color: 'var(--cds-text-secondary)'},
      {
        tag: [tags.string, tags.special(tags.string)],
        color: 'var(--cds-support-success)',
      },
      {
        tag: [tags.number, tags.bool, tags.null],
        color: 'var(--cds-link-primary)',
      },
    ]),
  ),
  EditorState.tabSize.of(2),
  EditorView.lineWrapping,
];

type Props = {
  value: string;
  label?: string;
  placeholder?: string;
  isTruncatedValue?: boolean;
  autoFocus?: boolean;
  onChange?: (value: string) => void;
  onValidate?: (isValid: boolean) => void;
  onBlur?: () => void;
  onFocus?: () => void;
  readOnly?: boolean;
  maxLines?: number;
  fieldError?: string;
  id?: string;
  'data-testid'?: string;
  renderButton?: () => React.ReactNode;
  onCopy?: () => Promise<string>;
};

function computeHeight(text: string, maxLines: number): number {
  const lineCount = Math.max(1, (text || '').split('\n').length);
  const textHeight = Math.min(lineCount, maxLines) * EDITOR_LINE_HEIGHT;

  return Math.max(EDITOR_MIN_HEIGHT, textHeight);
}

const InlineJsonEditor: React.FC<Props> = observer(
  ({
    value,
    label,
    onChange,
    onValidate,
    onBlur,
    onFocus,
    readOnly,
    placeholder = 'Value',
    isTruncatedValue = false,
    maxLines = EDITOR_MAX_LINES,
    id,
    fieldError,
    autoFocus,
    'data-testid': dataTestId,
    renderButton,
    onCopy,
  }) => {
    const isReadOnly = readOnly === true || onChange === undefined;

    const displayValue = useMemo(() => {
      if (!isReadOnly) {
        return value;
      }
      return isTruncatedValue
        ? beautifyTruncatedJSON(value)
        : beautifyJSON(value);
    }, [value, isTruncatedValue, isReadOnly]);

    const height = computeHeight(displayValue, maxLines);

    const handleChange = useCallback(
      (newValue: string) => {
        onChange?.(newValue);
        if (onValidate) {
          try {
            JSON.parse(newValue);
            onValidate(true);
          } catch {
            onValidate(false);
          }
        }
      },
      [onChange, onValidate],
    );

    const handleFocus = useCallback(
      (event: React.FocusEvent<HTMLDivElement>) => {
        if (
          event.relatedTarget instanceof Node &&
          event.currentTarget.contains(event.relatedTarget)
        ) {
          return;
        }
        onFocus?.();
      },
      [onFocus],
    );

    const handleBlur = useCallback(
      (event: React.FocusEvent<HTMLDivElement>) => {
        if (
          event.relatedTarget instanceof Node &&
          event.currentTarget.contains(event.relatedTarget)
        ) {
          return;
        }
        onBlur?.();
      },
      [onBlur],
    );

    const editorId = id === undefined ? undefined : `${id}-editor`;
    const extensions = useMemo(
      () => [
        ...baseExtensions,
        EditorView.contentAttributes.of({
          ...(editorId === undefined ? {} : {id: editorId}),
          'aria-label': label ?? 'Value',
          'aria-invalid': fieldError ? 'true' : 'false',
        }),
      ],
      [editorId, fieldError, label],
    );

    return (
      <EditorWrapper
        id={id}
        role="group"
        aria-label={label}
        data-testid={dataTestId ?? 'json-editor-wrapper'}
        onBlur={handleBlur}
        onFocus={handleFocus}
        $invalid={!!fieldError}
      >
        {isReadOnly ? (
          <ReadOnlyEditor
            data-testid={dataTestId}
            value={displayValue}
            placeholder={placeholder}
            isReadOnly={isReadOnly}
            maxLines={maxLines}
            fieldError={fieldError}
            label={label}
            height={height}
            renderButton={renderButton}
            onCopy={onCopy}
          />
        ) : (
          <>
            <label htmlFor={editorId} className="cds--visually-hidden">
              {label ?? 'Value'}
            </label>
            <WriteModeEditor $height={height} $invalid={!!fieldError}>
              <CompletionStyles />
              <CodeMirror
                data-testid="code-mirror-editor"
                value={displayValue}
                placeholder={placeholder}
                autoFocus={autoFocus}
                onChange={handleChange}
                onKeyDown={(event) => {
                  if (
                    event.key === 'Escape' &&
                    event.target instanceof HTMLElement
                  ) {
                    event.target.blur();
                  }
                }}
                extensions={extensions}
                basicSetup={basicSetup}
                indentWithTab={false}
                theme="none"
              />
            </WriteModeEditor>
          </>
        )}
        {fieldError && (
          <div className="cds--form-requirement">{fieldError}</div>
        )}
      </EditorWrapper>
    );
  },
);

export {InlineJsonEditor};
