import React from 'react';

import classnames from 'classnames';
import './Labeled.scss';

export default function Labeled({label, className, children, ...props}) {
  return (
    <div className={classnames('Labeled', className)}>
      <label {...props}>
        <span className="label">{label}</span>
        {children}
      </label>
    </div>
  );
}
