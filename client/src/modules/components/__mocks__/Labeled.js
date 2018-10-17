import React from 'react';

import classnames from 'classnames';

export default function Labeled({label, className, children, ...props}) {
  return (
    <div className={classnames('Labeled', className)}>
      <label {...props}>
        <span className="label">{label}</span>
      </label>
      {children}
    </div>
  );
}
