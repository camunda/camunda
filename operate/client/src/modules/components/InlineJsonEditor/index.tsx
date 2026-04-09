/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {lazy, Suspense, useCallback, useEffect, useMemo, useState} from 'react';
import {observer} from 'mobx-react-lite';
import {
  beautifyJSON,
  beautifyTruncatedJSON,
} from 'modules/utils/editor/beautifyJSON';
import {EditorLoader, EditorWrapper, WriteModeEditor} from './styled';
import {
  EDITOR_DECORATION_WIDTH,
  EDITOR_FONT_SIZE,
  EDITOR_FONT_FAMILY,
  EDITOR_LINE_HEIGHT,
  EDITOR_PADDING_BOTTOM,
  EDITOR_PADDING_TOP,
  EDITOR_MIN_HEIGHT,
  EDITOR_MAX_LINES,
} from './constants';
import {ReadOnlyEditor} from './ReadOnlyEditor';

const JSONEditor = lazy(async () => {
  const [{loadMonaco}, {JSONEditor}] = await Promise.all([
    import('modules/loadMonaco'),
    import('modules/components/JSONEditor'),
  ]);

  loadMonaco();

  return {default: JSONEditor};
});

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
  }) => {
    const isReadOnly = readOnly === true || onChange === undefined;

    const formattedValue = useMemo(() => {
      return isTruncatedValue
        ? beautifyTruncatedJSON(value)
        : beautifyJSON(value);
    }, [value, isTruncatedValue]);

    const [editingValue, setEditingValue] = useState<string | null>(null);
    const [isEditing, setIsEditing] = useState(false);

    const displayValue = useMemo(() => {
      if (isEditing && editingValue !== null) {
        return editingValue;
      }
      return formattedValue;
    }, [isEditing, editingValue, formattedValue]);

    const height = computeHeight(displayValue, maxLines);

    const handleChange = (newValue: string) => {
      onChange?.(newValue);
      setEditingValue(newValue);
      if (onValidate) {
        try {
          JSON.parse(newValue);
          onValidate(true);
        } catch {
          onValidate(false);
        }
      }
    };

    const handleFocus = useCallback(() => {
      setIsEditing(true);
      setEditingValue(null);
      onFocus?.();
    }, [onFocus]);

    const handleBlur = useCallback(() => {
      setIsEditing(false);
      setEditingValue(null);
      onBlur?.();
    }, [onBlur]);

    useEffect(() => {
      if (autoFocus) {
        handleFocus();
      }
    }, [autoFocus, handleFocus]);

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
        {isReadOnly || !isEditing ? (
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
          />
        ) : (
          <>
            <label htmlFor={id} className="cds--visually-hidden">
              {label ?? 'Value'}
            </label>
            <Suspense fallback={<EditorLoader $height={height} />}>
              <WriteModeEditor $invalid={!!fieldError}>
                <JSONEditor
                  loading={null}
                  value={displayValue}
                  onChange={isReadOnly ? undefined : handleChange}
                  readOnly={isReadOnly}
                  height={`${height}px`}
                  options={{
                    formatOnType: false,
                    lineNumbers: 'off',
                    lineDecorationsWidth: isReadOnly
                      ? 0
                      : EDITOR_DECORATION_WIDTH,
                    renderLineHighlight: 'none',
                    overviewRulerLanes: 0,
                    stickyScroll: {enabled: false},
                    glyphMargin: false,
                    folding: false,
                    scrollbar: {useShadows: false},
                    minimap: {enabled: false},
                    tabFocusMode: true,
                    fontSize: EDITOR_FONT_SIZE,
                    lineHeight: EDITOR_LINE_HEIGHT,
                    fontFamily: EDITOR_FONT_FAMILY,
                    padding: {
                      top: EDITOR_PADDING_TOP,
                      bottom: EDITOR_PADDING_BOTTOM,
                    },
                  }}
                />
              </WriteModeEditor>
            </Suspense>
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
