import React from 'react';

import './Select.css';

export default function Select(props) {
  const allowedProps = {...props};
  delete allowedProps.isInvalid;

  return (
    <select
      {...allowedProps}
      className={'Select ' + (props.className || '') + (props.isInvalid ? ' is-invalid' : '')}
    >
      {props.children}
    </select>
  );
}

Select.Option = function Option(props) {
  return <option {...props}>{props.children}</option>;
};
