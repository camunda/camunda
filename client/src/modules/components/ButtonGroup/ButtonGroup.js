import React from 'react';
import classnames from 'classnames';

import './ButtonGroup.css';

export default function ButtonGroup(props) {
  return <div className={classnames('ButtonGroup', props.className)}>{props.children}</div>;
}
