import React from 'react';

import './ButtonGroup.css';

export default function ButtonGroup(props) {
    return (<div className='ButtonGroup'>
      {props.children}
    </div>);
}
