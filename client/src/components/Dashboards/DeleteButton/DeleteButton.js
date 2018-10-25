import React from 'react';

import {Button} from 'components';

import './DeleteButton.scss';

export default function DeleteButton(props) {
  return (
    <Button
      className="DeleteButton"
      onClick={event => props.deleteReport({event, report: props.report})}
    />
  );
}
