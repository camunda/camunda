import React from 'react';
import {Link} from 'react-router-dom';

import './Button.css';

export default function Button({tag, label, type, id, title, onClick}) {
  if (tag === 'a') {
    return (<a className={(type ? 'Button Button--' + type : 'Button')} id={id} title={title} onClick={onClick}>
      {label}
    </a>);
  } else {
    return (<button className={(type ? 'Button--' + type : '')} id={id} title={title} onClick={onClick}>
      {label}
    </button>);
  }
}
