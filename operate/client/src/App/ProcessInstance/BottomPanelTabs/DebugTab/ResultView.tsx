/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Tabs, TabList, Tab, TabPanels, TabPanel} from '@carbon/react';
import {
  CheckmarkFilled,
  WarningAltFilled,
  ErrorFilled,
} from '@carbon/react/icons';
import type {EvaluateExpressionResponse} from 'modules/api/v2/expression/evaluateExpression';
import {
  ResultContainer,
  RawJson,
  Status,
  StatusIconSuccess,
  StatusIconWarning,
  StatusIconError,
  WarningList,
  WarningListTitle,
  WarningItem,
  ResultValue,
} from './styled';

type Props = {
  data:
    | {
        response: EvaluateExpressionResponse | null;
        error: unknown;
      }
    | undefined;
  error: Error | null;
  isPending: boolean;
};

const formatJson = (value: unknown) => JSON.stringify(value, null, 2);

const ResultView: React.FC<Props> = ({data, error, isPending}) => {
  if (isPending) {
    return <ResultContainer>Evaluating…</ResultContainer>;
  }

  if (error) {
    return (
      <ResultContainer>
        <Status>
          <StatusIconError>
            <ErrorFilled size={16} />
          </StatusIconError>
          Request failed: {error.message}
        </Status>
      </ResultContainer>
    );
  }

  if (data === undefined) {
    return null;
  }

  const response = data.response;
  const requestError = data.error;
  const warnings = response?.warnings ?? [];
  const hasWarnings = warnings.length > 0;
  const hasResponse = response !== null;

  return (
    <ResultContainer>
      <Tabs>
        <TabList aria-label="Expression evaluation result tabs">
          <Tab>Result</Tab>
          <Tab>Raw</Tab>
        </TabList>
        <TabPanels>
          <TabPanel>
            {hasResponse ? (
              <>
                <Status>
                  {hasWarnings ? (
                    <StatusIconWarning>
                      <WarningAltFilled size={16} />
                    </StatusIconWarning>
                  ) : (
                    <StatusIconSuccess>
                      <CheckmarkFilled size={16} />
                    </StatusIconSuccess>
                  )}
                  {hasWarnings
                    ? `Evaluated with ${warnings.length} warning${warnings.length === 1 ? '' : 's'}`
                    : 'Evaluated successfully'}
                </Status>
                <ResultValue>{formatJson(response.result)}</ResultValue>
                {hasWarnings && (
                  <WarningList>
                    <WarningListTitle>Warnings</WarningListTitle>
                    {warnings.map((warning, index) => (
                      <WarningItem key={index}>
                        <WarningAltFilled size={16} />
                        <span>{warning.message}</span>
                      </WarningItem>
                    ))}
                  </WarningList>
                )}
              </>
            ) : (
              <Status>
                <StatusIconError>
                  <ErrorFilled size={16} />
                </StatusIconError>
                Request failed
              </Status>
            )}
          </TabPanel>
          <TabPanel>
            <RawJson>{formatJson(response ?? requestError)}</RawJson>
          </TabPanel>
        </TabPanels>
      </Tabs>
    </ResultContainer>
  );
};

export {ResultView};
