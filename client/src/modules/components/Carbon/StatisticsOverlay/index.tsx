/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {createPortal} from 'react-dom';

import {Statistic} from './styled';
import {
  CheckmarkOutline,
  Error,
  RadioButtonChecked,
  WarningFilled,
} from '@carbon/react/icons';
import {observer} from 'mobx-react';
import {currentTheme} from 'modules/stores/currentTheme';

type Props = {
  flowNodeState: FlowNodeState;
  container: HTMLElement;
  count: number;
  isFaded?: boolean;
};

const StatisticsOverlay: React.FC<Props> = observer(
  ({flowNodeState, container, count, isFaded = false}) => {
    return createPortal(
      <Statistic
        $theme={currentTheme.theme}
        $state={flowNodeState}
        $isFaded={isFaded}
        orientation="horizontal"
        gap={3}
      >
        {flowNodeState === 'incidents' && <WarningFilled />}
        {flowNodeState === 'active' && <RadioButtonChecked />}
        {flowNodeState === 'completed' && <CheckmarkOutline />}
        {flowNodeState === 'canceled' && <Error />}
        <span>{count}</span>
      </Statistic>,
      container
    );
  }
);

export {StatisticsOverlay};
