import React from 'react';
import classnames from 'classnames';
import {Link} from 'react-router-dom';

import './Button.css';

export default React.forwardRef(function Button(props, ref) {
  const filteredProps = {...props};
  delete filteredProps.active;
  delete filteredProps.color;
  delete filteredProps.type;
  delete filteredProps.noStyle;
  let classNamesProp = {};

  if (!props.noStyle)
    classNamesProp = {
      className: classnames(props.className, 'Button', {
        ['Button--' + props.type]: props.type,
        ['Button--' + props.color]: props.color,
        'is-active': props.active
      })
    };

  if (props.tag === 'a') {
    return (
      <Link {...filteredProps} {...classNamesProp}>
        {props.children}
      </Link>
    );
  } else {
    return (
      <button {...filteredProps} {...classNamesProp} ref={ref}>
        {props.children}
      </button>
    );
  }
});
