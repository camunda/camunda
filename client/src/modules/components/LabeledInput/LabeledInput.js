import React from 'react';

import {Input} from 'components';
import classnames from 'classnames';

import './LabeledInput.css';

export default function LabeledInput({label, className, children, ...props}) {
  const id = props.id || generateId();
  return (
    <div className={classnames('LabeledInput', className)}>
      <label htmlFor={id}>
        <span className="LabeledInput__label">{label}</span>
      </label>
      <Input {...props} id={id} />
      {children}
    </div>
  );
}

function generateId() {
  return `input-${Math.random()
    .toString(36)
    .substr(2)}`;
}
