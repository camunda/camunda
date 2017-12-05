import React from 'react';

import './Input.css';

export default function Input(props) {
  const allowedProps = {...props};
  delete allowedProps.reference;

  return (<input type='text' {...allowedProps} className={'Input' + (props.className ? ' ' + props.className : '')} ref={props.reference}>
    {props.children}
  </input>);
}
