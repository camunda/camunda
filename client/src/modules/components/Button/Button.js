import React from 'react';
import classnames from 'classnames';
import {Link} from 'react-router-dom';

import './Button.css';

export default function Button(props) {
  const filteredProps = {...props};
  delete filteredProps.active;
  delete filteredProps.color;
  delete filteredProps.type;
  delete filteredProps.reference;
  if (props.tag === 'a') {
    return (
      <Link
        {...filteredProps}
        className={classnames(props.className, 'Button', {
          ['Button--' + props.type]: props.type,
          ['Button--' + props.color]: props.color,
          'is-active': props.active
        })}
      >
        {props.children}
      </Link>
    );
  } else {
    return (
      <button
        {...filteredProps}
        className={classnames(props.className, 'Button', {
          ['Button--' + props.type]: props.type,
          ['Button--' + props.color]: props.color,
          'is-active': props.active
        })}
        ref={props.reference}
      >
        {props.children}
      </button>
    );
  }
}
