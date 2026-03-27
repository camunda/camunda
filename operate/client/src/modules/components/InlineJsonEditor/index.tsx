/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {lazy, Suspense} from 'react';
import {observer} from 'mobx-react-lite';
import {beautifyJSON} from 'modules/utils/editor/beautifyJSON';
import {EditorLoader, EditorWrapper} from './styled';
import {useFieldError} from '../../hooks/useFieldError';

const LINE_HEIGHT = 20;
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
  onChange?: (value: string) => void;
  onValidate?: (isValid: boolean) => void;
  onBlur?: () => void;
  onFocus?: () => void;
  readOnly?: boolean;
  /** Max number of lines before a scrollbar appears. Default: 5 */
  maxLines?: number;
  id?: string;
  'data-testid'?: string;
};

function computeHeight(text: string, maxLines: number): number {
  const lineCount = Math.max(1, (text || '').split('\n').length);
  return Math.min(lineCount, maxLines) * LINE_HEIGHT;
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
    maxLines = MAX_LINES,
    id,
    'data-testid': dataTestId,
    fieldError,
  }) => {
    const isReadOnly = readOnly === true || onChange === undefined;
    const displayValue = beautifyJSON(value);
    const height = computeHeight(displayValue, maxLines);

    const handleChange = (newValue: string) => {
      onChange?.(newValue);

      if (onValidate) {
        try {
          JSON.parse(newValue);
          onValidate(true);
        } catch {
          onValidate(false);
        }
      }
    };

    // Avoid cursor jump while typing: use defaultValue when editable,
    // value only when read-only (to reflect external updates)
    const valueProps = readOnly
      ? {value: displayValue}
      : {defaultValue: displayValue};

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
            {...valueProps}
            onChange={isReadOnly ? undefined : handleChange}
            readOnly={isReadOnly}
            height={`${height}px`}
            width="100%"
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
