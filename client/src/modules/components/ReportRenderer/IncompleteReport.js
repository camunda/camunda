/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

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
