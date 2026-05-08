/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo, useRef, useState} from 'react';
import {useMutation} from '@tanstack/react-query';
import CodeMirror, {EditorView, Prec, keymap} from '@uiw/react-codemirror';
import {
  feel,
  keywordCompletions,
  snippetCompletion,
  snippets,
} from '@bpmn-io/lang-feel';
import type {
  CompletionContext,
  CompletionSource,
} from '@codemirror/autocomplete';
import {evaluateExpression} from 'modules/api/v2/expression/evaluateExpression';
import {useProcessInstancePageParams} from 'App/ProcessInstance/useProcessInstancePageParams';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import {useVariables} from 'modules/queries/variables/useVariables';
import {ResultView} from './ResultView';
import {
  Content,
  ContextHint,
  ExpressionEditor,
  ExpressionLabel,
  WarningFilled,
} from './styled';

const EXPRESSION_INPUT_ID = 'debug-expression-input';

const DebugTab: React.FC = () => {
  const [expression, setExpression] = useState('');
  const {processInstanceId} = useProcessInstancePageParams();
  const {resolvedElementInstance} = useProcessInstanceElementSelection();
  const isElementSelected =
    resolvedElementInstance != null &&
    resolvedElementInstance.elementInstanceKey !== processInstanceId;
  const useElementContext =
    isElementSelected && resolvedElementInstance.state === 'ACTIVE';
  const showInactiveElementWarning = isElementSelected && !useElementContext;

  const {mutate, reset, data, error, isPending} = useMutation({
    mutationFn: (value: string) => {
      const expression = value.trim().startsWith('=')
        ? value.trim()
        : `=${value}`.trim();
      const context = useElementContext
        ? {elementInstanceKey: resolvedElementInstance.elementInstanceKey}
        : {processInstanceKey: processInstanceId ?? ''};
      return evaluateExpression({expression, ...context});
    },
  });

  const {data: variablesData} = useVariables();
  const variableNamesRef = useRef<string[]>([]);
  useEffect(() => {
    variableNamesRef.current =
      variablesData?.pages.flatMap((page) =>
        page.items.map((variable) => variable.name),
      ) ?? [];
  }, [variablesData]);

  const variableCompletionSource = useMemo<CompletionSource>(
    () => (context: CompletionContext) => {
      const word = context.matchBefore(/[A-Za-z_$][\w$]*/);
      if (!word || (word.from === word.to && !context.explicit)) {
        return null;
      }
      const names = variableNamesRef.current;
      if (names.length === 0) {
        return null;
      }
      return {
        from: word.from,
        options: names.map((name) => ({
          label: name,
          type: 'variable',
        })),
        validFor: /^[\w$]*$/,
      };
    },
    [],
  );

  const handleChange = (value: string) => {
    setExpression(value);
    if (value.trim() === '') {
      reset();
    }
  };

  const selectedElementInstanceKey =
    resolvedElementInstance?.elementInstanceKey ?? null;
  useEffect(() => {
    reset();
  }, [selectedElementInstanceKey, reset]);

  const extensions = useMemo(
    () => [
      feel({
        dialect: 'expression',
        completions: [
          snippetCompletion(snippets),
          variableCompletionSource,
        ],
      }).extension,
      EditorView.lineWrapping,
      EditorView.theme(
        {
          '&': {
            backgroundColor: '#034',
          },
          '.cm-content': {
            caretColor: '#0e9',
          },
          '&.cm-focused .cm-cursor': {
            borderLeftColor: '#0e9',
          },
          '&.cm-focused .cm-selectionBackground, ::selection': {
            backgroundColor: '#074',
          },
          '.cm-gutters': {
            backgroundColor: '#045',
            border: 'none',
          },
        },
        {dark: true},
      ),
      EditorView.contentAttributes.of({'aria-label': 'FEEL expression'}),
      Prec.highest(
        keymap.of([
          {
            key: 'Enter',
            run: (view) => {
              const value = view.state.doc.toString();
              if (value.trim() !== '') {
                mutate(value);
              }
              return true;
            },
          },
        ]),
      ),
    ],
    [mutate, variableCompletionSource],
  );

  const basicSetup = useMemo(
    () => ({
      lineNumbers: false,
      foldGutter: false,
      highlightActiveLine: false,
      highlightActiveLineGutter: true,
      indentOnInput: false,
      bracketMatching: true,
      autocompletion: true,
      searchKeymap: true,
    }),
    [],
  );

  return (
    <Content>
      <ExpressionLabel htmlFor={EXPRESSION_INPUT_ID}>
        Expression
      </ExpressionLabel>
      <ExpressionEditor>
        <CodeMirror
          id={EXPRESSION_INPUT_ID}
          value={expression}
          placeholder="Enter a FEEL expression and press Enter"
          basicSetup={basicSetup}
          extensions={extensions}
          onChange={handleChange}
        />
      </ExpressionEditor>
      <ContextHint>
        {showInactiveElementWarning && (
          <WarningFilled
            size={16}
            aria-label="Selected element is not active"
          />
        )}
        {useElementContext
          ? `Evaluate against Element Instance ${resolvedElementInstance.elementInstanceKey}`
          : `Evaluate against Process Instance ${processInstanceId ?? ''}`}
        {showInactiveElementWarning &&
          ' — selected element is not active, falling back to the process instance'}
      </ContextHint>
      <ResultView data={data} error={error} isPending={isPending} />
    </Content>
  );
};

export {DebugTab};
