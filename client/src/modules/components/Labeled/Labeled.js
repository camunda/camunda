import React from 'react';

import classnames from 'classnames';
import './Labeled.scss';

export default function Labeled({label, className, children, ...props}) {
  return (
    <div className={classnames('Labeled', className)}>
      <label onClick={catchClick} {...props}>
        <span className="label">{label}</span>
        {children}
      </label>
    </div>
  );
}

function catchClick(evt) {
  if (evt.target.className !== 'label') evt.preventDefault();
}
