/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {lazy, Suspense, useEffect, useState} from 'react';
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
  readOnly?: boolean;
  /** Max number of lines before a scrollbar appears. Default: 5 */
  maxLines?: number;
  /** Whether to pretty-print the value on mount (view mode should pass true). Default: false */
  beautifyOnMount?: boolean;
  id?: string;
  'data-testid'?: string;
};

function computeHeight(text: string, maxLines: number): number {
  const lineCount = Math.max(1, (text || '').split('\n').length);
  return Math.min(lineCount, maxLines) * LINE_HEIGHT;
}

const InlineJsonEditor: React.FC<Props> = observer(
  ({
    name,
    value,
    onChange,
    onValidate,
    onBlur,
    readOnly,
    maxLines = MAX_LINES,
    beautifyOnMount = false,
    id,
    'data-testid': dataTestId,
  }) => {
    const isReadOnly = readOnly === true || onChange === undefined;
    const valueError = useFieldError(name ?? '');
    const [displayValue, setDisplayValue] = useState(
      isReadOnly ? beautifyJSON(value) : value,
    );
    const height = computeHeight(displayValue, maxLines);

    useEffect(() => {
      if (beautifyOnMount && !isReadOnly) {
        const beautified = beautifyJSON(value);
        setDisplayValue(beautified);
      }
      // intentionally run only on mount to set the initial display value
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

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
        height={height}
        $readOnly={isReadOnly}
        $invalid={!!valueError}
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
            options={{
              lineNumbers: 'off',
              lineDecorationsWidth: 0,
              stickyScroll: {enabled: false},
              glyphMargin: false,
              folding: false,
              scrollbar: {
                useShadows: false,
              },
            }}
          />
          {valueError && (
            <div className="cds--form-requirement">{valueError}</div>
          )}
        </Suspense>
      </EditorWrapper>
    );
  },
);

export {InlineJsonEditor};
