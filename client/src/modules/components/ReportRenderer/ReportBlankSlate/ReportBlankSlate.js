/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import './ReportBlankSlate.scss';

export default function ReportBlankSlate(props) {
  return (
    <div className="ReportBlankSlate">
      <p className="ReportBlankSlate__message">{props.errorMessage}</p>
      {!props.isCombined && (
        <React.Fragment>
          <div className="ReportBlankSlate__illustrationDropdown" />
          <ul className="ReportBlankSlate__diagramIllustrations">
            <li className="ReportBlankSlate__diagramIllustration ReportBlankSlate__diagramIllustration-1" />
            <li className="ReportBlankSlate__diagramIllustration ReportBlankSlate__diagramIllustration-2" />
            <li className="ReportBlankSlate__diagramIllustration ReportBlankSlate__diagramIllustration-3" />
          </ul>
        </React.Fragment>
      )}
    </div>
  );
}
