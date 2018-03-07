import React from 'react';

import {Input} from 'components';
import './Switch.css';

export default function Switch(props) {
  return (
    <label className="Switch">
      <Input type="checkbox" {...props}>
        {props.children}
      </Input>
      <span className="Switch__Slider--round" />
    </label>
  );
}
