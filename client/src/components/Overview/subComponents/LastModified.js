import React from 'react';
import moment from 'moment';

import './LastModified.scss';

export default function LastModified({date, author}) {
  return (
    <span className="LastModified">
      Last modified {moment(date).format('lll')}
      <br />by <strong>{author}</strong>
    </span>
  );
}
