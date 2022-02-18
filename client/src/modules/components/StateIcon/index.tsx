/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {STATE} from 'modules/constants';

import * as Styled from './styled';

const stateIconsMap = {
  FAILED: Styled.IncidentIcon,
  [STATE.INCIDENT]: Styled.IncidentIcon,
  [STATE.ACTIVE]: Styled.ActiveIcon,
  [STATE.COMPLETED]: Styled.CompletedIcon,
  [STATE.CANCELED]: Styled.CanceledIcon,
  [STATE.TERMINATED]: Styled.CanceledIcon,
};

type Props = {
  state?: InstanceEntityState | DecisionInstanceEntityState;
};

function StateIcon({state, ...props}: Props) {
  if (state === undefined) {
    return <Styled.AliasIcon data-testid="alias-icon" {...props} />;
  }

  const TargetComponent = stateIconsMap[state];
  return <TargetComponent data-testid={`${state}-icon`} {...props} />;
}

export default StateIcon;
