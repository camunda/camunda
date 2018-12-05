import React from 'react';
import classnames from 'classnames';

import './ButtonGroup.scss';

export default function ButtonGroup(props) {
  return (
    <div className={classnames('ButtonGroup', {disabled: props.disabled}, props.className)}>
      {props.children}
    </div>
  );
}
