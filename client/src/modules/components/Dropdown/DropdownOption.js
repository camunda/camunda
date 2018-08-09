import React from 'react';
import {Icon} from 'components';
import './DropdownOption.css';
import classnames from 'classnames';

export default function DropdownOption({active, ...props}) {
  return (
    <div
      {...props}
      onClick={evt => !props.disabled && props.onClick && props.onClick(evt)}
      className={classnames('DropdownOption', props.className, {'is-active': active})}
    >
      {props.checked && <Icon className="checkMark" type="check-small" size="10px" />}
      {props.children}
    </div>
  );
}
