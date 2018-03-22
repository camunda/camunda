import React from 'react';
import classnames from 'classnames';
import './ControlGroup.css';

export default function ControlGroup(props) {
  return (
    <div
      className={classnames(props.className, 'ControlGroup', {
        ['ControlGroup--' + props.layout]: props.layout
      })}
    >
      {props.children}
    </div>
  );
}
