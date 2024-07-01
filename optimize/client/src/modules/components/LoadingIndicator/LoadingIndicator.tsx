/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ComponentPropsWithoutRef} from 'react';
import classnames from 'classnames';

import './LoadingIndicator.scss';

interface LoadingIndicatorProps extends ComponentPropsWithoutRef<'div'> {
  small?: boolean;
}

export default function LoadingIndicator({
  small,
  className,
  ...props
}: LoadingIndicatorProps): JSX.Element {
  return <div {...props} className={classnames('LoadingIndicator', className, {small})} />;
}
