/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {PANEL_POSITION} from 'modules/constants';

import CollapsablePanel from 'modules/components/CollapsablePanel';

export default function OperationsPanel() {
  return (
    <CollapsablePanel
      label="Operations"
      panelPosition={PANEL_POSITION.RIGHT}
      maxWidth={350}
      isOverlay
    >
      <div style={{padding: '20px'}}>Body</div>
    </CollapsablePanel>
  );
}
