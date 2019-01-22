import React from 'react';
import classnames from 'classnames';

import {ButtonGroup, Button} from 'components';
import './AllColumnsButtons.scss';

export default function AllColumnsButtons({allEnabled, allDisabled, enableAll, disableAll}) {
  return (
    <ButtonGroup className="AllColumnsButtons">
      <Button color={classnames({green: allEnabled})} onClick={enableAll}>
        Enable All
      </Button>
      <Button color={classnames({green: allDisabled})} onClick={disableAll}>
        Disable All
      </Button>
    </ButtonGroup>
  );
}
