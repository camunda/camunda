import React from 'react';
import {Link} from 'react-router-dom';

import './Button.css';

export default function Button(props) {
  if (props.tag === 'a') {
    return (<Link {...props} className={'Button ' + (props.type ? ('Button--' + props.type) : '') + (props.className || '')}>
      {props.children}
    </Link>);
  } else {
    return (<button {...props} className={'Button ' + (props.type ? ('Button--' + props.type) : '') + (props.className || '')}>
      {props.children}
    </button>);
  }
}
