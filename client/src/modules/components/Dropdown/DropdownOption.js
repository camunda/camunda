import React from 'react';
import {Button} from 'components';
import './DropdownOption.css';
import classnames from 'classnames';

export default function DropdownOption(props) {
  return (
    <Button {...props} className={classnames('DropdownOption', props.className)}>
      {props.children}
    </Button>
  );
}
