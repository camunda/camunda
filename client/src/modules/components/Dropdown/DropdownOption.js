import React from 'react';
import {Button} from 'components';
import './DropdownOption.css';

export default function DropdownOption(props) {
  return (
    <Button {...props} className="DropdownOption">
      {props.children}
    </Button>
  );
}
