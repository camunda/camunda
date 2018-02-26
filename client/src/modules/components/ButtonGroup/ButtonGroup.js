import React from 'react';

import './ButtonGroup.css';

export default function ButtonGroup(props) {
    return (<div className={props.className ? 'ButtonGroup ' + props.className : 'ButtonGroup'}>
      {props.children}
    </div>);
}
