/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Link, withRouter} from 'react-router-dom';

import './IncompleteReport.scss';
import {t} from 'translation';

export function IncompleteReport({id, location}) {
  return (
    <div className="IncompleteReport">
      <p>
        {t('report.incompleteNotice.message')}
        <br />
        <Link to={`/report/${id}/edit?returnTo=${location.pathname}`}>
          {t('report.incompleteNotice.action')}
        </Link>
      </p>
    </div>
  );
}

export default withRouter(IncompleteReport);
