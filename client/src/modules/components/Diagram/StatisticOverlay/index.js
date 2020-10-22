/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';

import {ReactComponent as IncidentIcon} from 'modules/components/Icon/diagram-badge-single-instance-incident.svg';
import {ReactComponent as ActiveIcon} from 'modules/components/Icon/diagram-badge-single-instance-active.svg';
import {ReactComponent as CompletedLightIcon} from 'modules/components/Icon/diagram-badge-single-instance-completed-light.svg';
import {ReactComponent as CompletedDarkIcon} from 'modules/components/Icon/diagram-badge-single-instance-completed-dark.svg';
import {ReactComponent as CanceledLightIcon} from 'modules/components/Icon/diagram-badge-single-instance-canceled-light.svg';
import {ReactComponent as CanceledDarkIcon} from 'modules/components/Icon/diagram-badge-single-instance-canceled-dark.svg';
import {STATISTICS_OVERLAY_ID} from 'modules/constants';
import {currentTheme, THEME_NAME} from 'modules/stores/currentTheme';
import Overlay from '../Overlay';

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
};

function StatisticOverlay(props) {
  const {
    statistic,
    state,
    onOverlayAdd,
    onOverlayClear,
    isViewerLoaded,
  } = props;
  const isLightTheme = currentTheme.state.selectedTheme === THEME_NAME.LIGHT;

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
      TargetIcon = isLightTheme ? CanceledLightIcon : CanceledDarkIcon;
      break;
    case 'completed':
      TargetIcon = isLightTheme ? CompletedLightIcon : CompletedDarkIcon;
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

StatisticOverlay.propTypes = {
  statistic: PropTypes.shape({
    activityId: PropTypes.string.isRequired,
  }).isRequired,
  state: PropTypes.oneOf(['active', 'incidents', 'canceled', 'completed'])
    .isRequired,
  onOverlayAdd: PropTypes.func.isRequired,
  onOverlayClear: PropTypes.func.isRequired,
  isViewerLoaded: PropTypes.bool.isRequired,
};

export default observer(StatisticOverlay);
