/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import {ReactComponent as IncidentIcon} from 'modules/components/Icon/diagram-badge-single-instance-incident.svg';
import {ReactComponent as ActiveIcon} from 'modules/components/Icon/diagram-badge-single-instance-active.svg';
import {ReactComponent as CompletedLightIcon} from 'modules/components/Icon/diagram-badge-single-instance-completed-light.svg';
import {ReactComponent as CompletedDarkIcon} from 'modules/components/Icon/diagram-badge-single-instance-completed-dark.svg';
import {ReactComponent as CanceledLightIcon} from 'modules/components/Icon/diagram-badge-single-instance-canceled-light.svg';
import {ReactComponent as CanceledDarkIcon} from 'modules/components/Icon/diagram-badge-single-instance-canceled-dark.svg';
import {FLOW_NODE_STATE_OVERLAY_ID, STATE} from 'modules/constants';

import Overlay from '../Overlay';

const position = {
  bottom: 17,
  left: -7,
};

export default function StateOverlay(props) {
  const {
    id,
    state,
    onOverlayAdd,
    onOverlayClear,
    isViewerLoaded,
    theme,
  } = props;

  let TargetIcon;

  switch (state) {
    case STATE.INCIDENT:
      TargetIcon = IncidentIcon;
      break;
    case STATE.ACTIVE:
      TargetIcon = ActiveIcon;
      break;
    case STATE.COMPLETED:
      TargetIcon = theme === 'light' ? CompletedLightIcon : CompletedDarkIcon;
      break;
    case STATE.TERMINATED:
      TargetIcon = theme === 'light' ? CanceledLightIcon : CanceledDarkIcon;
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

StateOverlay.propTypes = {
  id: PropTypes.string.isRequired,
  state: PropTypes.oneOf(Object.keys(STATE)).isRequired,
  onOverlayAdd: PropTypes.func.isRequired,
  onOverlayClear: PropTypes.func.isRequired,
  isViewerLoaded: PropTypes.bool.isRequired,
  theme: PropTypes.oneOf(['dark', 'light']).isRequired,
};
