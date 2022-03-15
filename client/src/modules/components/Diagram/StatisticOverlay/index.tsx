/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {observer} from 'mobx-react';

import {ReactComponent as IncidentIcon} from 'modules/components/Icon/diagram-badge-single-instance-incident.svg';
import {ReactComponent as ActiveIcon} from 'modules/components/Icon/diagram-badge-single-instance-active.svg';
import {ReactComponent as CompletedIcon} from 'modules/components/Icon/diagram-badge-single-instance-completed.svg';
import {ReactComponent as CanceledIcon} from 'modules/components/Icon/diagram-badge-single-instance-canceled.svg';
import {STATISTICS_OVERLAY_ID} from 'modules/constants';
import {OverlayType} from 'modules/types/modeler';
import {Overlay} from '../Overlay';

import * as Styled from './styled';

const positions = {
  active: {
    bottom: 9,
    left: 0,
  },
  incidents: {
    bottom: 9,
    right: 0,
  },
  canceled: {
    top: -16,
    left: 0,
  },
  completed: {
    bottom: 1,
    left: 17,
  },
} as const;

type Props = {
  statistic: {
    activityId: string;
    active?: string;
    incidents?: string;
    canceled?: string;
    completed?: string;
  };
  state: 'active' | 'incidents' | 'canceled' | 'completed';
  onOverlayAdd: (id: string, type: string, overlay: OverlayType) => void;
  onOverlayClear: ({element}: {element: HTMLDivElement}) => void;
  isViewerLoaded: boolean;
};

const StatisticOverlay: React.FC<Props> = observer(
  ({statistic, state, onOverlayAdd, onOverlayClear, isViewerLoaded}) => {
    if (!statistic[state]) {
      return null;
    }

    let TargetIcon;

    switch (state) {
      case 'active':
        TargetIcon = ActiveIcon;
        break;
      case 'incidents':
        TargetIcon = IncidentIcon;
        break;
      case 'canceled':
        TargetIcon = CanceledIcon;
        break;
      case 'completed':
        TargetIcon = CompletedIcon;
        break;
      default:
        TargetIcon = () => null;
    }

    return (
      <Overlay
        onOverlayAdd={onOverlayAdd}
        onOverlayClear={onOverlayClear}
        isViewerLoaded={isViewerLoaded}
        id={statistic.activityId}
        type={STATISTICS_OVERLAY_ID}
        position={positions[state]}
      >
        <Styled.Statistic state={state}>
          <TargetIcon width={24} height={24} />
          <Styled.StatisticSpan>{statistic[state]}</Styled.StatisticSpan>
        </Styled.Statistic>
      </Overlay>
    );
  }
);

export {StatisticOverlay};
