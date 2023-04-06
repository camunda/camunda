/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ComponentPropsWithoutRef, FormEvent} from 'react';

import classnames from 'classnames';

import './Form.scss';

interface FormProps extends Omit<ComponentPropsWithoutRef<'form'>, 'title'> {
  title?: string | JSX.Element[];
  description?: string | JSX.Element[];
  compact?: boolean;
  horizontal?: boolean;
  onSubmit?: (evt: FormEvent<HTMLFormElement>) => void;
}

export default function Form({
  compact,
  title,
  description,
  horizontal,
  onSubmit,
  ...props
}: FormProps) {
  return (
    <form
      {...props}
      onSubmit={(evt) => {
        evt.preventDefault();
        onSubmit?.(evt);
      }}
      className={classnames('Form', {compact, horizontal}, props.className)}
    >
      {title && <h3 className="formTitle">{title}</h3>}
      {description && <p className="formDescription">{description}</p>}
      {props.children}
    </form>
  );
}

interface FormGroupProps extends ComponentPropsWithoutRef<'div'> {
  noSpacing?: boolean;
}

Form.Group = function FormGroup({noSpacing, ...props}: FormGroupProps) {
  return (
    <div {...props} className={classnames('FormGroup', {noSpacing}, props.className)}>
      {props.children}
    </div>
  );
};

Form.InputGroup = function InputGroup(props: ComponentPropsWithoutRef<'div'>) {
  return (
    <div {...props} className={classnames('InputGroup', props.className)}>
      {props.children}
    </div>
  );
};
