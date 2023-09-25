/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ProcessInstance} from 'modules/types';
import {WarningFilled, CheckmarkOutline, RadioButtonChecked} from './styled';
import {Icon, Error} from '@carbon/react/icons';

const stateIconsMap = {
  incident: WarningFilled,
  active: RadioButtonChecked,
  completed: CheckmarkOutline,
  canceled: Error,
  terminated: Error,
} as const;

type Props = {
  state: ProcessInstance['state'];
  size: React.ComponentProps<Icon>['size'];
};

const ProcessInstanceStateIcon: React.FC<Props> = ({state, ...props}) => {
  const TargetComponent = stateIconsMap[state];
  return <TargetComponent data-testid={`${state}-icon`} {...props} />;
};

export {ProcessInstanceStateIcon};
