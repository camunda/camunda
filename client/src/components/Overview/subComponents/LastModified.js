import React from 'react';
import moment from 'moment';

import './LastModified.scss';

export default function LastModified({label, date, author}) {
  return (
    <span className="LastModified">
      {label} {moment(date).format('lll')}
      <br />
      by <strong>{author}</strong>
    </span>
  );
}
