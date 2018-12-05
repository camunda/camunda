import React from 'react';
import {Button} from 'components';

import './NoEntities.scss';

export default function NoEntities({label, createFunction}) {
  return (
    <li className="NoEntities">
      There are no {label}s configured.
      <Button type="link" className="createLink" onClick={createFunction}>
        Create a new {label}…
      </Button>
    </li>
  );
}
