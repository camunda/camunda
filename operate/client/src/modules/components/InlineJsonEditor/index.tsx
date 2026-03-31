/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useEffect, useMemo, useState} from 'react';
import {observer} from 'mobx-react-lite';
import {
  beautifyJSON,
  beautifyTruncatedJSON,
} from 'modules/utils/editor/beautifyJSON';
import {EditorLoader, EditorReadonly, EditorWrapper} from './styled';
import {useFieldError} from '../../hooks/useFieldError';
import {
  EDITOR_DECORATION_WIDTH,
  EDITOR_FONT_SIZE,
  EDITOR_FONT_FAMILY,
  EDITOR_LINE_HEIGHT,
  EDITOR_PADDING_BOTTOM,
  EDITOR_PADDING_TOP,
} from './constants';
import {debounce} from 'lodash';
import {useEditor} from '../../../App/ProcessInstance/BottomPanelTabs/VariablesTab/Variables/EditorContext/useEditor';

const MIN_HEIGHT = 32;
const MAX_LINES = 5;

type Props = {
  name?: string;
  value: string;
  placeholder?: string;
  isTruncatedValue?: boolean;
  shouldFocusOnMount?: boolean;
  onChange?: (value: string) => void;
  onValidate?: (isValid: boolean) => void;
  onBlur?: () => void;
  onFocus?: () => void;
  readOnly?: boolean;
  maxLines?: number;
  id?: string;
};

function computeHeight(text: string, maxLines: number): number {
  const lineCount = Math.max(1, (text || '').split('\n').length);
  const textHeight = Math.min(lineCount, maxLines) * EDITOR_LINE_HEIGHT;

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
    placeholder = 'Value',
    isTruncatedValue = false,
    maxLines = MAX_LINES,
    id,
    fieldError,
    shouldFocusOnMount,
  }) => {
    const Editor = useEditor();
    const isReadOnly = readOnly === true || onChange === undefined;

    const formattedValue = useMemo(() => {
      return isTruncatedValue
        ? beautifyTruncatedJSON(value)
        : beautifyJSON(value);
    }, [value, isTruncatedValue]);

    const [displayValue, setDisplayValue] = useState(formattedValue);
    const [isEditing, setIsEditing] = useState(false);
    const height = computeHeight(displayValue, maxLines);

    // Sync the external `value` into `displayValue` in two cases:
    // 1. Read-only mode: always reflect the latest value (e.g. after saving).
    // 2. Edit mode: only when `value` diverges from `displayValue`, meaning the
    //    change came from outside (e.g. modal apply) and not from the user typing
    //    (handleChange keeps them in sync, so keystrokes never trigger this).
    useEffect(() => {
      if (isReadOnly || value !== displayValue) {
        setDisplayValue(formattedValue);
      }
    }, [isReadOnly, value, displayValue, formattedValue]);

    const debouncedValidate = useMemo(() => {
      return debounce((val: string) => {
        try {
          JSON.parse(val);
          onValidate?.(true);
        } catch {
          onValidate?.(false);
        }
      }, 300);
    }, [onValidate]);

    useEffect(() => {
      return () => {
        debouncedValidate.cancel();
      };
    }, [debouncedValidate]);

    const handleChange = (newValue: string) => {
      onChange?.(newValue);
      setDisplayValue(newValue);
      debouncedValidate(newValue);
    };

    return (
      <EditorWrapper
        tabIndex={0}
        id={id}
        data-testid="json-editor-wrapper"
        onBlur={() => {
          setIsEditing(false);
          onBlur?.();
        }}
        onFocus={() => {
          setIsEditing(true);
          onFocus?.();
        }}
        $invalid={!!fieldError}
      >
        {isReadOnly || !isEditing ? (
          <EditorReadonly
            data-testid="json-editor-readonly"
            $height={height}
            $empty={displayValue === ''}
            $editMode={!isReadOnly}
          >
            {displayValue || placeholder}
          </EditorReadonly>
        ) : (
          <>
            <label htmlFor="value" className="cds--visually-hidden">
              Value
            </label>
            {!Editor ? (
              <EditorLoader $height={height} />
            ) : (
              <Editor
                value={displayValue}
                onChange={isReadOnly ? undefined : handleChange}
                readOnly={isReadOnly}
                height={`${height}px`}
                shouldFocusOnMount={shouldFocusOnMount}
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
                  fontSize: EDITOR_FONT_SIZE,
                  lineHeight: EDITOR_LINE_HEIGHT,
                  fontFamily: EDITOR_FONT_FAMILY,
                  padding: {
                    top: EDITOR_PADDING_TOP,
                    bottom: EDITOR_PADDING_BOTTOM,
                  },
                }}
              />
            )}
            {fieldError && (
              <div className="cds--form-requirement">{fieldError}</div>
            )}
          </>
        )}
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
