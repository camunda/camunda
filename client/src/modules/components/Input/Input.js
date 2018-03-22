import React from 'react';
import classnames from 'classnames';

import './Input.css';

export default function Input(props) {
  const allowedProps = {...props};
  delete allowedProps.reference;
  delete allowedProps.isInvalid;

  return (
    <input
      type="text"
      {...allowedProps}
      className={classnames('Input', props.className, {
        'is-invalid': props.isInvalid
      })}
      ref={props.reference}
    >
      {props.children}
    </input>
  );
}
