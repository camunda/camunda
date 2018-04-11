import React from 'react';

import classnames from 'classnames';

import './ErrorMessage.css';

export default function ErrorMessage(props) {
  return <div className={classnames('ErrorMessage', props.className)}>{props.text}</div>;
}
