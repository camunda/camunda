/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Link} from 'react-router-dom';

import {Icon, Tooltip} from 'components';
import './DropdownOption.scss';
import classnames from 'classnames';

export default React.forwardRef(function DropdownOption({active, link, disabled, ...props}, ref) {
  const commonProps = {
    ...props,
    className: classnames('DropdownOption', props.className, {'is-active': active, disabled}),
    tabIndex: disabled ? '-1' : '0',
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
        <Link {...commonProps} to={link}>
          {content}
        </Link>
      </Tooltip>
    );
  }
  return (
    <Tooltip content={content} overflowOnly>
      <div {...commonProps} onClick={(evt) => !disabled && props.onClick && props.onClick(evt)}>
        {content}
      </div>
    </Tooltip>
  );
});
