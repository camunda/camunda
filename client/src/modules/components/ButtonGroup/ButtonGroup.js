import React from 'react';

import './ButtonGroup.css';

export default function ButtonGroup(props) {
    return (<div className={'ButtonGroup' + (props.className && ` ${props.className}`)}>
      {props.children}
    </div>);
}
