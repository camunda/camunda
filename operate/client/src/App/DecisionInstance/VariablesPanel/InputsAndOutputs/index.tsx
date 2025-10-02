/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo, useRef, useState} from 'react';
import {
  ResizablePanel,
  SplitDirection,
} from 'modules/components/ResizablePanel';
import {
  Container,
  Panel,
  Title,
  StructuredList,
  EmptyMessage,
  ErrorMessage,
} from './styled';
import {Skeleton} from './Skeleton';
import type {DecisionInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {useDecisionInstance} from 'modules/queries/decisionInstances/useDecisionInstance';

type RowProps = React.ComponentProps<typeof StructuredList>['rows'][number];

const inputMappingsColumns = [
  {
    cellContent: 'Name',
    width: '30%',
  },
  {
    cellContent: 'Value',
    width: '70%',
  },
];

const outputMappingsColumns = [
  {
    cellContent: 'Rule',
    width: '20%',
  },
  {
    cellContent: 'Name',
    width: '40%',
  },
  {
    cellContent: 'Value',
    width: '40%',
  },
];

type InputAndOutputProps = {
  decisionEvaluationInstanceKey: DecisionInstance['decisionEvaluationInstanceKey'];
};

const InputsAndOutputs: React.FC<InputAndOutputProps> = ({
  decisionEvaluationInstanceKey,
}) => {
  const {data: decisionInstance, status} = useDecisionInstance(
    decisionEvaluationInstanceKey,
  );

  const containerRef = useRef<HTMLDivElement | null>(null);
  const [clientWidth, setClientWidth] = useState(0);

  useEffect(() => {
    setClientWidth(containerRef?.current?.clientWidth ?? 0);
  }, []);

  const panelMinWidth = clientWidth / 3;

  const evaluatedInputsRows = useMemo<RowProps[]>(() => {
    if (!decisionInstance?.evaluatedInputs?.length) {
      return [];
    }

    return decisionInstance.evaluatedInputs.map((input) => ({
      key: input.inputId,
      columns: [
        {cellContent: input.inputName},
        {cellContent: input.inputValue},
      ],
    }));
  }, [decisionInstance?.evaluatedInputs]);

  const evaluatedOutputRows = useMemo<RowProps[]>(() => {
    if (!decisionInstance?.matchedRules?.length) {
      return [];
    }

    return decisionInstance.matchedRules.flatMap((rule) => {
      return rule.evaluatedOutputs.map<RowProps>((output) => ({
        key: `${output.outputId}--${rule.ruleId}`,
        columns: [
          {cellContent: rule.ruleIndex},
          {cellContent: output.outputName},
          {cellContent: output.outputValue},
        ],
      }));
    });
  }, [decisionInstance?.matchedRules]);

  return (
    <Container ref={containerRef}>
      <ResizablePanel
        panelId="decision-instance-horizontal-panel"
        direction={SplitDirection.Horizontal}
        minWidths={[panelMinWidth, panelMinWidth]}
      >
        <Panel aria-label="input variables">
          {status !== 'error' && <Title>Inputs</Title>}
          {status === 'pending' && (
            <Skeleton
              dataTestId="inputs-skeleton"
              columnWidths={inputMappingsColumns.map(({width}) => width)}
            />
          )}
          {status === 'success' &&
            decisionInstance.state === 'FAILED' &&
            evaluatedInputsRows.length === 0 && (
              <EmptyMessage message="No input available because the evaluation failed" />
            )}
          {status === 'success' && evaluatedInputsRows.length > 0 && (
            <StructuredList
              label="Inputs"
              headerSize="sm"
              isFlush={false}
              headerColumns={inputMappingsColumns}
              rows={evaluatedInputsRows}
            />
          )}
          {status === 'error' && <ErrorMessage />}
        </Panel>
        <Panel aria-label="output variables">
          {status !== 'error' && <Title>Outputs</Title>}
          {status === 'pending' && (
            <Skeleton
              dataTestId="outputs-skeleton"
              columnWidths={outputMappingsColumns.map(({width}) => width)}
            />
          )}
          {status === 'success' && decisionInstance.state !== 'FAILED' && (
            <StructuredList
              label="Outputs"
              headerSize="sm"
              isFlush={false}
              headerColumns={outputMappingsColumns}
              rows={evaluatedOutputRows}
            />
          )}
          {status === 'error' && <ErrorMessage />}
          {status === 'success' && decisionInstance.state === 'FAILED' && (
            <EmptyMessage message="No output available because the evaluation failed" />
          )}
        </Panel>
      </ResizablePanel>
    </Container>
  );
};

export {InputsAndOutputs};
