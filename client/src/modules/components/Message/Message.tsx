/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ReactNode} from 'react';
import classnames from 'classnames';

import './Message.scss';

type MessageProps = {
  error?: boolean;
  className?: string;
  children?: ReactNode;
};

export default function Message({error, className, children}: MessageProps): JSX.Element {
  return <div className={classnames('Message', className, {error})}>{children}</div>;
}
