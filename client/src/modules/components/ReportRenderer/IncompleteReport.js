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
  const renderLink = () => {
    const currentUrl = window.location.href;
    if (currentUrl.includes('/share')) {
      const baseUrl = currentUrl.substring(0, currentUrl.indexOf('#')).replace('external/', '');
      return (
        <a
          href={`${baseUrl}#/report/${id}/edit`}
          target="_blank"
          rel="noopener noreferrer"
          className="title-button"
        >
          {t('report.incompleteNotice.action')}
        </a>
      );
    } else {
      return (
        <Link to={`/report/${id}/edit?returnTo=${location.pathname}`}>
          {t('report.incompleteNotice.action')}
        </Link>
      );
    }
  };

  return (
    <div className="IncompleteReport">
      <p>
        {t('report.incompleteNotice.message')}
        <br />
        {renderLink()}
      </p>
    </div>
  );
}

export default withRouter(IncompleteReport);
