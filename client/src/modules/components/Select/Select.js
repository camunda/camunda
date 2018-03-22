import React from 'react';
import classnames from 'classnames';

import './Select.css';

export default function Select(props) {
  const allowedProps = {...props};
  delete allowedProps.isInvalid;

  return (
    <select
      {...allowedProps}
      className={classnames('Select', props.className, {
        'is-invalid': props.isInvalid
      })}
    >
      {props.children}
    </select>
  );
}

Select.Option = function Option(props) {
  return <option {...props}>{props.children}</option>;
};
