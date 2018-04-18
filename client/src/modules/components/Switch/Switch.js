import React from 'react';
import classnames from 'classnames';

import {Input} from 'components';
import './Switch.css';

export default function Switch(props) {
  return (
    <label className={classnames('Switch', props.className)}>
      <Input type="checkbox" {...props} className="Switch__Input" />
      <span className="Switch__Slider--round" />
    </label>
  );
}
