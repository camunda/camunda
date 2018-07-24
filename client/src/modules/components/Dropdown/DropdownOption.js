import React from 'react';
import {Button, Icon} from 'components';
import './DropdownOption.css';
import classnames from 'classnames';

export default function DropdownOption(props) {
  return (
    <Button {...props} className={classnames('DropdownOption', props.className)}>
      {props.checked && (
        <Icon className="DropdownOption__checkMark" type="check-small" size="10px" />
      )}
      {props.children}
    </Button>
  );
}
