/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {forwardRef, ComponentPropsWithoutRef} from 'react';
import classnames from 'classnames';

import './Button.scss';

interface ButtonProps extends ComponentPropsWithoutRef<'button'> {
  active?: boolean;
  main?: boolean;
  primary?: boolean;
  warning?: boolean;
  icon?: boolean;
  small?: boolean;
  link?: boolean;
}

export default forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  {active, main, primary, warning, icon, small, className, link, onClick, ...rest},
  ref
): JSX.Element {
  return (
    <button
      type="button"
      {...rest}
      className={classnames(className, 'Button', {
        primary,
        main,
        warning,
        icon,
        small,
        link,
        active,
      })}
      ref={ref}
      onClick={onClick}
    />
  );
});
