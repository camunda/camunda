import React from 'react';
import classnames from 'classnames';

import {Input} from 'components';
import './Switch.scss';

export default function Switch(props) {
  return (
    <label title={props.title} className={classnames('Switch', props.className)}>
      <Input type="checkbox" {...props} className="Switch__Input" />
      <span className={classnames('Switch__Slider--round', {disabled: props.disabled})} />
    </label>
  );
}
