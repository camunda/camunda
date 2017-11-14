import React from 'react';

import './Select.css';

export default function Select(props) {
  return <select {...props} className={'Select ' + (props.className || '')}>
    {props.children}
  </select>
}

Select.Option = function Option(props) {
  return <option {...props}>
    {props.children}
  </option>
};
