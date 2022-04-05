/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ReactComponent as IncidentIcon} from 'modules/components/Icon/diagram-badge-single-instance-incident.svg';
import {ReactComponent as CompletedIcon} from 'modules/components/Icon/diagram-badge-single-instance-completed.svg';
import {createPortal} from 'react-dom';

type Props = {
  state: DecisionInstanceEntityState;
  container: HTMLElement;
};

const DecisionState: React.FC<Props> = ({state, container}) => {
  return createPortal(
    <>
      {state === 'FAILED' && <IncidentIcon />}
      {state === 'EVALUATED' && <CompletedIcon />}
    </>,
    container
  );
};

export {DecisionState};
