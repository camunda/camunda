/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {observer} from 'mobx-react';

import {ReactComponent as IncidentIcon} from 'modules/components/Icon/diagram-badge-single-instance-incident.svg';
import {ReactComponent as ActiveIcon} from 'modules/components/Icon/diagram-badge-single-instance-active.svg';
import {ReactComponent as CompletedIcon} from 'modules/components/Icon/diagram-badge-single-instance-completed.svg';
import {ReactComponent as CanceledIcon} from 'modules/components/Icon/diagram-badge-single-instance-canceled.svg';
import {FLOW_NODE_STATE_OVERLAY_ID} from 'modules/constants';
import {OverlayType} from 'modules/types/modeler';
import {Overlay} from '../Overlay';

const position = {
  bottom: 17,
  left: -7,
};

type Props = {
  id: string;
  state: InstanceEntityState;
  onOverlayAdd: (id: string, type: string, overlay: OverlayType) => void;
  onOverlayClear: ({element}: {element: HTMLDivElement}) => void;
  isViewerLoaded: boolean;
};

const StateOverlay: React.FC<Props> = observer(
  ({id, state, onOverlayAdd, onOverlayClear, isViewerLoaded}) => {
    let TargetIcon;

    switch (state) {
      case 'INCIDENT':
        TargetIcon = IncidentIcon;
        break;
      case 'ACTIVE':
        TargetIcon = ActiveIcon;
        break;
      case 'COMPLETED':
        TargetIcon = CompletedIcon;
        break;
      case 'TERMINATED':
        TargetIcon = CanceledIcon;
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
);

export {StateOverlay};
