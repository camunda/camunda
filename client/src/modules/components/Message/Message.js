import React from 'react';
import classnames from 'classnames';

import './Message.css';

export default function Message({type, children}) {
  return (
    <div
      className={classnames('Message', {
        ['Message--' + type]: type
      })}
    >
      {children}
    </div>
  );
}
