import React from 'react';

import './NoDataNotice.scss';

export default function NoDataNotice({children}) {
  return (
    <div className="NoDataNotice">
      <h1>No data to display</h1>
      <p>{children}</p>
    </div>
  );
}
