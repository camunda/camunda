/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {parseISO} from 'date-fns';

import {format} from 'dates';
import {t} from 'translation';

import SingleReportDetails from './SingleReportDetails';

import './ReportDetails.scss';

export default function ReportDetails({report}) {
  return (
    <div className="ReportDetails">
      {report.combined ? (
        Object.values(report.result.data).map((report) => (
          <SingleReportDetails key={report.id} showReportName report={report} />
        ))
      ) : (
        <SingleReportDetails report={report} />
      )}

      <dl>
        <dt>{t('common.entity.createdBy')}</dt>
        <dd>{report.owner}</dd>

        <dt>{t('common.entity.modifiedTitle')}</dt>
        <dd>
          {format(parseISO(report.lastModified), 'PPp')}
          <br />
          {t('common.entity.byModifier', {modifier: report.lastModifier})}
        </dd>
      </dl>
    </div>
  );
}
