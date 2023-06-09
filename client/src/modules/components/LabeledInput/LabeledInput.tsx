/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {forwardRef, ReactNode} from 'react';
import {Labeled, Input, LabeledProps, InputProps} from 'components';

import classnames from 'classnames';

interface LabeledInputProps extends InputProps {
  label: LabeledProps['label'];
  className?: string;
  children?: ReactNode;
}

const LabeledInput = forwardRef<HTMLInputElement, LabeledInputProps>(function LabeledInput(
  {label, className, children, ...props},
  ref
): JSX.Element {
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

export default LabeledInput;
