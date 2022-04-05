/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import * as Styled from './styled';

const stateIconsMap = {
  FAILED: Styled.IncidentIcon,
  INCIDENT: Styled.IncidentIcon,
  ACTIVE: Styled.ActiveIcon,
  COMPLETED: Styled.CompletedIcon,
  EVALUATED: Styled.CompletedIcon,
  CANCELED: Styled.CanceledIcon,
  TERMINATED: Styled.CanceledIcon,
} as const;

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
