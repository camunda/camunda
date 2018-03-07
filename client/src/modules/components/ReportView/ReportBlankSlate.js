import React from 'react';

import './ReportBlankSlate.css';

export default function ReportBlankSlate(props) {
  return (
    <div className="ReportBlankSlate">
      <p className="ReportBlankSlate__message">{props.message}</p>
      <div className="ReportBlankSlate__illustrationDropdown" />
      <ul className="ReportBlankSlate__diagramIllustrations">
        <li className="ReportBlankSlate__diagramIllustration ReportBlankSlate__diagramIllustration-1" />
        <li className="ReportBlankSlate__diagramIllustration ReportBlankSlate__diagramIllustration-2" />
        <li className="ReportBlankSlate__diagramIllustration ReportBlankSlate__diagramIllustration-3" />
      </ul>
    </div>
  );
}
