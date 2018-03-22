import React from 'react';
import classnames from 'classnames';

import './Message.css';

export default function Message({type, message}) {
  return (
    <div
      className={classnames('Message', {
        ['Message--' + type]: type
      })}
    >
      {message}
    </div>
  );
}
