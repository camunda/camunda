/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useMemo} from 'react';
import {observer} from 'mobx-react-lite';
import {
  beautifyJSON,
  beautifyTruncatedJSON,
} from 'modules/utils/editor/beautifyJSON';
import {EditorWrapper, WriteModeEditor} from './styled';
import {
  EDITOR_LINE_HEIGHT,
  EDITOR_MIN_HEIGHT,
  EDITOR_MAX_LINES,
} from './constants';
import {ReadOnlyEditor} from './ReadOnlyEditor';
import {CodeMirrorEditor} from './CodeMirrorEditor';

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

    const formattedValue = useMemo(() => {
      return isTruncatedValue
        ? beautifyTruncatedJSON(value)
        : beautifyJSON(value);
    }, [value, isTruncatedValue]);

    const displayValue = isReadOnly ? formattedValue : value;

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
              <CodeMirrorEditor
                value={displayValue}
                label={label ?? 'Value'}
                placeholder={placeholder}
                id={editorId}
                autoFocus={autoFocus}
                invalid={!!fieldError}
                onChange={handleChange}
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
