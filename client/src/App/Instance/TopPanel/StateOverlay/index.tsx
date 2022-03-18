/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observer} from 'mobx-react';
import {ReactComponent as IncidentIcon} from 'modules/components/Icon/diagram-badge-single-instance-incident.svg';
import {ReactComponent as ActiveIcon} from 'modules/components/Icon/diagram-badge-single-instance-active.svg';
import {ReactComponent as CompletedIcon} from 'modules/components/Icon/diagram-badge-single-instance-completed.svg';
import {ReactComponent as CanceledIcon} from 'modules/components/Icon/diagram-badge-single-instance-canceled.svg';
import {createPortal} from 'react-dom';

type Props = {
  state: InstanceEntityState;
  container: HTMLElement;
};

const StateOverlay: React.FC<Props> = observer(({state, container}) => {
  return createPortal(
    <>
      {state === 'INCIDENT' && <IncidentIcon />}
      {state === 'ACTIVE' && <ActiveIcon />}
      {state === 'COMPLETED' && <CompletedIcon />}
      {state === 'TERMINATED' && <CanceledIcon />}
    </>,
    container
  );
});

export {StateOverlay};
