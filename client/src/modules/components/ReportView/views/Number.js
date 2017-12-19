import React from 'react';
import ReportBlankSlate from '../ReportBlankSlate';

import './Number.css';

export default function Number({data, errorMessage}) {
  if(typeof data !== 'number') {
    return <ReportBlankSlate message={errorMessage} />;
  }

  return <span className='Number'>{data}</span>;
}
