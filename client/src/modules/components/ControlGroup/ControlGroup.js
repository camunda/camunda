import React from 'react';

import './ControlGroup.css';

export default function ControlGroup(props) {
  return (
    <div
      className={
        'ControlGroup' +
        (props.className ? ' ' + props.className : '') +
        (props.layout ? ' ControlGroup--' + props.layout : '')
      }
    >
      {props.children}
    </div>
  );
}
