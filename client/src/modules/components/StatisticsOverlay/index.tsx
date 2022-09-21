/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {createPortal} from 'react-dom';

import {Statistic, StatisticSpan} from './styled';
import {ReactComponent as IncidentIcon} from 'modules/components/Icon/diagram-badge-single-instance-incident.svg';
import {ReactComponent as ActiveIcon} from 'modules/components/Icon/diagram-badge-single-instance-active.svg';
import {ReactComponent as CompletedIcon} from 'modules/components/Icon/diagram-badge-single-instance-completed.svg';
import {ReactComponent as CanceledIcon} from 'modules/components/Icon/diagram-badge-single-instance-canceled.svg';

type Props = {
  flowNodeState: FlowNodeState;
  container: HTMLElement;
  count: number;
  isFaded?: boolean;
};

const StatisticsOverlay: React.FC<Props> = ({
  flowNodeState,
  container,
  count,
  isFaded = false,
}) => {
  return createPortal(
    <Statistic $state={flowNodeState} $isFaded={isFaded}>
      {flowNodeState === 'incidents' && <IncidentIcon />}
      {flowNodeState === 'active' && <ActiveIcon />}
      {flowNodeState === 'completed' && <CompletedIcon />}
      {flowNodeState === 'canceled' && <CanceledIcon />}
      <StatisticSpan>{count}</StatisticSpan>
    </Statistic>,
    container
  );
};

export {StatisticsOverlay};
