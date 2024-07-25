/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
