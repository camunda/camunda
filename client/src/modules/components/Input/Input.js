import React from 'react';
import classnames from 'classnames';

import './Input.scss';

export default React.forwardRef(function Input(props, ref) {
  const allowedProps = {...props};
  delete allowedProps.isInvalid;

  return (
    <input
      type="text"
      {...allowedProps}
      className={classnames('Input', props.className, {
        isInvalid: props.isInvalid
      })}
      ref={ref}
    >
      {props.children}
    </input>
  );
});
