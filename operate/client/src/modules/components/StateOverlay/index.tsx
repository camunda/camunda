/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createPortal} from 'react-dom';

import {Container} from './styled';
import {
  CheckmarkOutline,
  Error,
  RadioButtonChecked,
  WarningFilled,
} from '@carbon/react/icons';
import {observer} from 'mobx-react';
import {currentTheme} from 'modules/stores/currentTheme';
import type {FlowNodeState} from 'modules/types/operate';
import type {DecisionInstanceState} from '@camunda/camunda-api-zod-schemas/8.8';

type Props = {
  state: (FlowNodeState | 'completedEndEvents') | DecisionInstanceState;
  container: HTMLElement;
  count?: number;
  isFaded?: boolean;
  testId?: string;
  title?: string;
};

const StateOverlay: React.FC<Props> = observer(
  ({
    state,
    container,
    count,
    isFaded = false,
    testId = `state-overlay-${state}`,
    title,
  }) => {
    const showStatistic = count !== undefined;

    return createPortal(
      <Container
        data-testid={testId}
        title={title}
        $theme={currentTheme.theme}
        $state={state}
        $isFaded={isFaded}
        orientation="horizontal"
        gap={3}
        $showStatistic={showStatistic}
      >
        {['FAILED', 'incidents'].includes(state) && <WarningFilled />}
        {state === 'active' && <RadioButtonChecked />}
        {['EVALUATED', 'completedEndEvents', 'completed'].includes(state) && (
          <CheckmarkOutline />
        )}
        {state === 'canceled' && <Error />}
        {showStatistic && <span>{count}</span>}
      </Container>,
      container,
    );
  },
);

export {StateOverlay};
