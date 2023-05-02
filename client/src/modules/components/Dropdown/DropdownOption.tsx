/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ComponentPropsWithoutRef, ForwardedRef, forwardRef} from 'react';
import {Link} from 'react-router-dom';
import classnames from 'classnames';

import {Icon, Tooltip} from 'components';

import './DropdownOption.scss';

interface CommonProps<T extends object | string | number = string> {
  value?: T;
  active?: boolean;
  disabled?: boolean;
  checked?: boolean;
  label?: string | JSX.Element;
}

interface LinkProps<T extends object | string | number = string>
  extends CommonProps<T>,
    Partial<ComponentPropsWithoutRef<Link>> {
  link: string;
}

interface DivProps<T extends object | string | number = string>
  extends CommonProps<T>,
    Partial<ComponentPropsWithoutRef<'div'>> {
  link?: never;
}

export type DropdownOptionProps<T extends object | string | number = string> =
  | LinkProps<T>
  | DivProps<T>;

function DropdownOption<T extends object | string | number = string>(
  {active, link, disabled, ...props}: DropdownOptionProps<T>,
  ref: ForwardedRef<HTMLElement>
) {
  const commonProps = {
    ...props,
    className: classnames('DropdownOption', props.className, {'is-active': active, disabled}),
    tabIndex: disabled ? -1 : 0,
    ref,
  };

  const content = (
    <>
      {props.checked && <Icon className="checkMark" type="check-small" size="10px" />}
      {props.children}
    </>
  );

  if (link) {
    return (
      <Tooltip content={content} overflowOnly>
        <Link {...(commonProps as Partial<LinkProps>)} to={link}>
          {content}
        </Link>
      </Tooltip>
    );
  }

  return (
    <Tooltip content={content} overflowOnly>
      <div
        {...(commonProps as Partial<DivProps>)}
        onClick={(evt) => !disabled && (props as DivProps).onClick?.(evt)}
      >
        {content}
      </div>
    </Tooltip>
  );
}

export default forwardRef(DropdownOption) as <T extends object | string | number = string>(
  props: DropdownOptionProps<T> & {ref?: ForwardedRef<HTMLElement>}
) => ReturnType<typeof DropdownOption>;
