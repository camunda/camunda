/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Button, Icon} from 'components';
import './ZoomControls.scss';

const zoomStepSize = 0.1;

export default function ZoomControls({fit, zoom}) {
  return (
    <div className="ZoomControls">
      <Button className="reset" onClick={fit}>
        <Icon type="diagram-reset" />
      </Button>
      <Button className="zoomIn" onClick={() => zoom(zoomStepSize)}>
        <Icon type="plus" />
      </Button>
      <Button className="zoomOut" onClick={() => zoom(-zoomStepSize)}>
        <Icon type="minus" />
      </Button>
    </div>
  );
}
