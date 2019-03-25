import React from 'react';
import {Link} from 'react-router-dom';

import './IncompleteReport.scss';

export default function IncompleteReport({id}) {
  return (
    <div className="IncompleteReport">
      <p>
        To display this Report,
        <br />
        <Link to={`/report/${id}/edit`}>complete set-up…</Link>
      </p>
    </div>
  );
}
