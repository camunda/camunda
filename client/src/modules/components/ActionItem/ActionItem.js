import React from 'react';
import classnames from 'classnames';
import {Button} from 'components';

import './ActionItem.scss';

export default function ActionItem(props) {
  return (
    <div className="ActionItem">
      <Button disabled={props.disabled} onClick={props.onClick} className="ActionItem__button">
        ×
      </Button>
      <span className={classnames('ActionItem__content', props.className)}>{props.children}</span>
    </div>
  );
}
