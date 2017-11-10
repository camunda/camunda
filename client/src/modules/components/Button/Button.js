import React from 'react';
import {Link} from 'react-router-dom';

import './Button.css';

export default function Button(props) {
  if (props.tag === 'a') {
    return (<Link className={(props.type ? 'Button Button--' + props.type : 'Button')} {...props}>
      {props.children}
    </Link>);
  } else {
    return (<button className={(props.type ? 'Button Button--' + props.type : 'Button')} {...props}>
      {props.children}
    </button>);
  }
}
