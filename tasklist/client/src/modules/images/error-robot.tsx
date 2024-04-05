/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import Icon from './error-robot.svg?react';

const ErrorRobot: React.FC<
  Omit<React.ComponentProps<typeof Icon>, 'focusable' | 'aria-hidden'>
> = (props) => {
  return <Icon {...props} focusable={false} aria-hidden />;
};

export {ErrorRobot};
