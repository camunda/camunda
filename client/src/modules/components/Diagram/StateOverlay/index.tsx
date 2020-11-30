/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {observer} from 'mobx-react';

import {ReactComponent as IncidentIcon} from 'modules/components/Icon/diagram-badge-single-instance-incident.svg';
import {ReactComponent as ActiveIcon} from 'modules/components/Icon/diagram-badge-single-instance-active.svg';
import {ReactComponent as CompletedLightIcon} from 'modules/components/Icon/diagram-badge-single-instance-completed-light.svg';
import {ReactComponent as CompletedDarkIcon} from 'modules/components/Icon/diagram-badge-single-instance-completed-dark.svg';
import {ReactComponent as CanceledLightIcon} from 'modules/components/Icon/diagram-badge-single-instance-canceled-light.svg';
import {ReactComponent as CanceledDarkIcon} from 'modules/components/Icon/diagram-badge-single-instance-canceled-dark.svg';
import {FLOW_NODE_STATE_OVERLAY_ID, STATE} from 'modules/constants';
import Overlay from '../Overlay';
import {currentTheme} from 'modules/stores/currentTheme';

const position = {
  bottom: 17,
  left: -7,
};

type Props = {
  id: string;
  state: InstanceEntityState;
  onOverlayAdd: (...args: any[]) => any;
  onOverlayClear: (...args: any[]) => any;
  isViewerLoaded: boolean;
};

function StateOverlay(props: Props) {
  const {id, state, onOverlayAdd, onOverlayClear, isViewerLoaded} = props;

  let TargetIcon;

  switch (state) {
    case STATE.INCIDENT:
      TargetIcon = IncidentIcon;
      break;
    case STATE.ACTIVE:
      TargetIcon = ActiveIcon;
      break;
    case STATE.COMPLETED:
      TargetIcon =
        currentTheme.state.selectedTheme === 'light'
          ? CompletedLightIcon
          : CompletedDarkIcon;
      break;
    case STATE.TERMINATED:
      TargetIcon =
        currentTheme.state.selectedTheme === 'light'
          ? CanceledLightIcon
          : CanceledDarkIcon;
      break;
    default:
      TargetIcon = () => null;
  }

  return (
    <Overlay
      id={id}
      type={FLOW_NODE_STATE_OVERLAY_ID}
      position={position}
      onOverlayAdd={onOverlayAdd}
      onOverlayClear={onOverlayClear}
      isViewerLoaded={isViewerLoaded}
    >
      <TargetIcon />
    </Overlay>
  );
}

export default observer(StateOverlay);
