import React from 'react';

import {Button} from 'components';

import './ActionItem.css';

export default function ActionItem(props) {
  return <React.Fragment>
    <Button onClick={props.onClick} className='ActionItem__button'>×</Button>
    <span className='ActionItem__content'>
      {props.children}
    </span>
  </React.Fragment>;
}
