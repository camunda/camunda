/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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

type Props = {
  state: FlowNodeState | DecisionInstanceEntityState;
  container: HTMLElement;
  count?: number;
  isFaded?: boolean;
};

const StateOverlay: React.FC<Props> = observer(
  ({state, container, count, isFaded = false}) => {
    const showStatistic = count !== undefined;

    return createPortal(
      <Container
        data-testid="state-overlay"
        $theme={currentTheme.theme}
        $state={state}
        $isFaded={isFaded}
        orientation="horizontal"
        gap={3}
        $showStatistic={showStatistic}
      >
        {['FAILED', 'incidents'].includes(state) && <WarningFilled />}
        {state === 'active' && <RadioButtonChecked />}
        {['EVALUATED', 'completed'].includes(state) && <CheckmarkOutline />}
        {state === 'canceled' && <Error />}
        {showStatistic && <span>{count}</span>}
      </Container>,
      container,
    );
  },
);

export {StateOverlay};
