import React from 'react';

import classnames from 'classnames';
import './Labeled.css';

export default function Labeled({label, className, children, ...props}) {
  const id = props.id || generateId();
  return (
    <div className={classnames('Labeled', className)}>
      <label htmlFor={id}>
        <span className="label">{label}</span>
      </label>
      {React.cloneElement(children, {id})}
    </div>
  );
}

function generateId() {
  return `input-${Math.random()
    .toString(36)
    .substr(2)}`;
}
