/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {lazy, Suspense, useEffect, useState} from 'react';
import {observer} from 'mobx-react-lite';
import {
  beautifyJSON,
  beautifyTruncatedJSON,
} from 'modules/utils/editor/beautifyJSON';
import {EditorLoader, EditorWrapper} from './styled';
import {useFieldError} from '../../hooks/useFieldError';

const LINE_HEIGHT = 22;
const MIN_HEIGHT = 28;
const MAX_LINES = 5;

const JSONEditor = lazy(async () => {
  const [{loadMonaco}, {JSONEditor}] = await Promise.all([
    import('modules/loadMonaco'),
    import('modules/components/JSONEditor'),
  ]);

  loadMonaco();

  return {default: JSONEditor};
});

type Props = {
  name?: string;
  value: string;
  isTruncatedValue?: boolean;
  onChange?: (value: string) => void;
  onValidate?: (isValid: boolean) => void;
  onBlur?: () => void;
  onFocus?: () => void;
  readOnly?: boolean;
  maxLines?: number;
  id?: string;
  'data-testid'?: string;
};

function computeHeight(text: string, maxLines: number): number {
  const lineCount = Math.max(1, (text || '').split('\n').length);
  const textHeight = Math.min(lineCount, maxLines) * LINE_HEIGHT;

  return Math.max(MIN_HEIGHT, textHeight);
}

type InnerProps = Props & {fieldError?: string};

const InlineJsonEditorInner: React.FC<InnerProps> = observer(
  ({
    value,
    onChange,
    onValidate,
    onBlur,
    onFocus,
    readOnly,
    isTruncatedValue = false,
    maxLines = MAX_LINES,
    id,
    'data-testid': dataTestId,
    fieldError,
  }) => {
    const isReadOnly = readOnly === true || onChange === undefined;
    const [displayValue, setDisplayValue] = useState(
      isTruncatedValue ? beautifyTruncatedJSON(value) : beautifyJSON(value),
    );
    const height = computeHeight(displayValue, maxLines);

    // Sync the external `value` into `displayValue` in two cases:
    // 1. Read-only mode: always reflect the latest value (e.g. after saving).
    // 2. Edit mode: only when `value` diverges from `displayValue`, meaning the
    //    change came from outside (e.g. modal apply) and not from the user typing
    //    (handleChange keeps them in sync, so keystrokes never trigger this).
    useEffect(() => {
      if (isReadOnly || value !== displayValue) {
        setDisplayValue(
          isTruncatedValue ? beautifyTruncatedJSON(value) : beautifyJSON(value),
        );
      }
    }, [isReadOnly, value, isTruncatedValue, displayValue]);

    const handleChange = (newValue: string) => {
      onChange?.(newValue);
      setDisplayValue(newValue);

      if (onValidate) {
        try {
          JSON.parse(newValue);
          onValidate(true);
        } catch {
          onValidate(false);
        }
      }
    };

    return (
      <EditorWrapper
        id={id}
        data-testid={dataTestId}
        onBlur={onBlur}
        onFocus={onFocus}
        height={height}
        $readOnly={isReadOnly}
        $invalid={!!fieldError}
      >
        <Suspense fallback={<EditorLoader height={height} />}>
          <label htmlFor="value" className="cds--visually-hidden">
            Value
          </label>
          <JSONEditor
            value={displayValue}
            onChange={isReadOnly ? undefined : handleChange}
            readOnly={isReadOnly}
            height={`${height}px`}
            width="100%"
            shouldFocusOnMount={false}
            options={{
              formatOnType: false,
              lineNumbers: 'off',
              lineDecorationsWidth: 0,
              renderLineHighlight: 'none',
              stickyScroll: {enabled: false},
              glyphMargin: false,
              folding: false,
              scrollbar: {
                useShadows: false,
              },
              minimap: {enabled: false},
            }}
          />
          {fieldError && (
            <div className="cds--form-requirement">{fieldError}</div>
          )}
        </Suspense>
      </EditorWrapper>
    );
  },
);

/**
 * Form-aware wrapper: reads the field error from react-final-form context.
 * Only rendered when `name` is provided, so the hook is never called outside a Form.
 */
const InlineJsonEditorWithFormField: React.FC<Props & {name: string}> = (
  props,
) => {
  const fieldError = useFieldError(props.name);
  return <InlineJsonEditorInner {...props} fieldError={fieldError} />;
};

const InlineJsonEditor: React.FC<Props> = (props) => {
  if (props.name) {
    return <InlineJsonEditorWithFormField {...props} name={props.name} />;
  }
  return <InlineJsonEditorInner {...props} />;
};

export {InlineJsonEditor};
