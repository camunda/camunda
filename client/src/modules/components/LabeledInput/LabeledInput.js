/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {Labeled, Input} from 'components';
import classnames from 'classnames';

export default React.forwardRef(function LabeledInput({label, className, children, ...props}, ref) {
  return (
    <div className={classnames('LabeledInput', className)}>
      <Labeled
        label={label}
        appendLabel={props.type === 'checkbox' || props.type === 'radio'}
        disabled={props.disabled}
      >
        <Input ref={ref} {...props} />
      </Labeled>
      {children}
    </div>
  );
});
