/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ComponentPropsWithoutRef} from 'react';
import classnames from 'classnames';

import './MessageBox.scss';

interface MessageBoxProps extends ComponentPropsWithoutRef<'div'> {
  type: 'error' | 'success' | 'warning';
}

export default function MessageBox({type, ...props}: MessageBoxProps) {
  return (
    <div
      {...props}
      className={classnames('MessageBox', {
        ['MessageBox--' + type]: type,
      })}
    />
  );
}
