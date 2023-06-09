/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ComponentPropsWithoutRef} from 'react';
import classnames from 'classnames';

import './ButtonGroup.scss';

interface ButtonGroupProps extends ComponentPropsWithoutRef<'div'> {
  disabled?: boolean;
}

export default function ButtonGroup(props: ButtonGroupProps) {
  return (
    <div className={classnames('ButtonGroup', {disabled: props.disabled}, props.className)}>
      {props.children}
    </div>
  );
}
