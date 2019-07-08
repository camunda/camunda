/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import RelativeAbsoluteSelection from './subComponents/RelativeAbsoluteSelection';

export default function HeatmapConfig(props) {
  const {
    report: {data},
    onChange
  } = props;
  return (
    <fieldset>
      <legend>Tooltips</legend>
      <RelativeAbsoluteSelection
        hideRelative={data.view.property !== 'frequency'}
        absolute={data.configuration.alwaysShowAbsolute}
        relative={data.configuration.alwaysShowRelative}
        onChange={(type, value) => {
          if (type === 'absolute') {
            onChange({alwaysShowAbsolute: {$set: value}});
          } else {
            onChange({alwaysShowRelative: {$set: value}});
          }
        }}
      />
    </fieldset>
  );
}
