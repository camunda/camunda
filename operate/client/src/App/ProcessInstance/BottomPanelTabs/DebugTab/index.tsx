/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState, type KeyboardEvent} from 'react';
import {useMutation} from '@tanstack/react-query';
import {evaluateExpression} from 'modules/api/v2/expression/evaluateExpression';
import {useProcessInstancePageParams} from 'App/ProcessInstance/useProcessInstancePageParams';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import {ResultView} from './ResultView';
import {
  Content,
  ContextHint,
  FullWidthTextInput,
  WarningFilled,
} from './styled';

const DebugTab: React.FC = () => {
  const [expression, setExpression] = useState('');
  const {processInstanceId} = useProcessInstancePageParams();
  const {resolvedElementInstance} = useProcessInstanceElementSelection();
  const isElementSelected =
    resolvedElementInstance != null &&
    resolvedElementInstance.elementInstanceKey !== processInstanceId;
  const useElementContext =
    isElementSelected && resolvedElementInstance.state === 'ACTIVE';
  const showInactiveElementWarning =
    isElementSelected && !useElementContext;

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

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter' && expression.trim() !== '') {
      event.preventDefault();
      mutate(expression);
    }
  };

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

  return (
    <Content>
      <FullWidthTextInput
        id="debug-expression-input"
        labelText="Expression"
        placeholder="Enter a FEEL expression and press Enter"
        value={expression}
        onChange={({target}) => handleChange(target.value)}
        onKeyDown={handleKeyDown}
      />
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
