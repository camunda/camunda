import React from 'react';
import classnames from 'classnames';
import {Button} from 'components';

import './ActionItem.css';

export default function ActionItem(props) {
  return (
    <React.Fragment>
      <Button disabled={props.disabled} onClick={props.onClick} className="ActionItem__button">
        ×
      </Button>
      <span className={classnames('ActionItem__content', props.className)}>{props.children}</span>
    </React.Fragment>
  );
}
