/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ComponentPropsWithoutRef, forwardRef} from 'react';
import {Link} from 'react-router-dom';
import classnames from 'classnames';

import {Icon, Tooltip} from 'components';

import './DropdownOption.scss';

interface CommonProps {
  value?: string;
  active?: boolean;
  disabled?: boolean;
  checked?: boolean;
  label?: string | JSX.Element;
}

interface LinkProps extends CommonProps, Partial<ComponentPropsWithoutRef<Link>> {
  link: string;
}

interface DivProps extends CommonProps, Partial<ComponentPropsWithoutRef<'div'>> {
  link?: never;
}

export type DropdownOptionProps = LinkProps | DivProps;

export default forwardRef(function DropdownOption(props: DropdownOptionProps, ref) {
  const {active, link, disabled, ...rest} = props;
  const commonProps = {
    ...rest,
    className: classnames('DropdownOption', rest.className, {'is-active': active, disabled}),
    tabIndex: disabled ? -1 : 0,
    ref,
  };

  const content = (
    <>
      {rest.checked && <Icon className="checkMark" type="check-small" size="10px" />}
      {rest.children}
    </>
  );

  if (isAnchor(props)) {
    return (
      <Tooltip content={content} overflowOnly>
        <Link {...(commonProps as Partial<LinkProps>)} to={props.link}>
          {content}
        </Link>
      </Tooltip>
    );
  }

  return (
    <Tooltip content={content} overflowOnly>
      <div
        {...(commonProps as Partial<DivProps>)}
        onClick={(evt) => !disabled && props.onClick?.(evt)}
      >
        {content}
      </div>
    </Tooltip>
  );
});

function isAnchor(props: DropdownOptionProps): props is LinkProps {
  return !!props.link;
}
