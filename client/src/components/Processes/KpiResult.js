/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Link} from 'react-router-dom';
import classnames from 'classnames';

import {t} from 'translation';
import {formatters} from 'services';
import {Icon, NoDataNotice} from 'components';

import {isSuccessful} from './service';

import './KpiResult.scss';

export default function KpiResult({kpis, displayTip}) {
  if (!kpis || kpis.every((report) => !report.value)) {
    return (
      <div className="KpiResult">
        <NoDataNotice type="info">
          {t('processes.timeGoals.noInstances')} {displayTip && t('processes.timeGoals.setGoals')}
        </NoDataNotice>
      </div>
    );
  }

  return (
    <div className="KpiResult">
      {kpis?.map(
        ({reportId, reportName, value, unit, target, isBelow, measure, collectionId}, idx) => {
          const formatter = formatters[measure];

          let link = `/report/${reportId}/`;
          if (collectionId) {
            link = `/collection/${collectionId}` + link;
          }

          return (
            <div key={idx} className="kpi">
              <b className="title">
                {t('report.label')}: {reportName}
              </b>{' '}
              <Link to={link} target="_blank">
                <Icon type="jump" />
              </Link>
              <div className="reportValues">
                <span
                  className={classnames(
                    {success: isSuccessful({target, unit, value, isBelow, measure})},
                    'reportValue'
                  )}
                >
                  {t('common.value')}:{' '}
                  {measure === 'duration' ? formatter(value, 3) : formatter(value)}
                </span>
                <span>
                  {t('report.config.goal.target')}: {target}
                  {measure === 'percentage' && '%'} {unit}
                </span>
              </div>
            </div>
          );
        }
      )}
    </div>
  );
}
