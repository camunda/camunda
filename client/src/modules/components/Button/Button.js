import React from 'react';
import {Link} from 'react-router-dom';

import './Button.css';

export default function Button(props) {
  const filteredProps = {...props};
  delete filteredProps.active;
  delete filteredProps.color;
  delete filteredProps.type;
  if (props.tag === 'a') {
    return (<Link 
              {...filteredProps} 
                className={'Button ' 
                  + (props.type ? ('Button--' + props.type + ' ') : '') 
                  + (props.color ? ('Button--' + props.color + ' ') : '') 
                  + (props.className ? props.className + ' ' : '')
                  + (props.active ? 'is-active' : '')
              }>
              {props.children}
            </Link>);
  } else {
    return (<button 
              {...filteredProps} 
                className={'Button ' 
                + (props.type ? ('Button--' + props.type + ' ') : '') 
                + (props.color ? ('Button--' + props.color + ' ') : '') 
                + (props.className ? props.className + ' ' : '')
                + (props.active ? 'is-active' : '')
              }>
              {props.children}
            </button>);
  }
}
