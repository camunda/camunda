import React from 'react';

import './SetupNotice.scss';

export default function SetupNotice({children}) {
  return (
    <div className="SetupNotice">
      <h1>Set-up your Report</h1>
      {children}
    </div>
  );
}
