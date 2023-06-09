/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ComponentPropsWithRef} from 'react';
import classnames from 'classnames';

import './Badge.scss';

interface BadgeProps extends ComponentPropsWithRef<'span'> {}

export default function Badge({className, ...props}: BadgeProps) {
  return <span {...props} className={classnames('Badge', className)} />;
}
