import React from 'react';

import './Input.css';

export default function Input(props) {
  const allowedProps = {...props};
  delete allowedProps.reference;
  delete allowedProps.isInvalid;

  return (
    <input
      type="text"
      {...allowedProps}
      className={
        'Input' +
        (props.className ? ' ' + props.className : '') +
        (props.isInvalid ? ' is-invalid' : '')
      }
      ref={props.reference}
    >
      {props.children}
    </input>
  );
}
