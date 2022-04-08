/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import deepEqual from 'fast-deep-equal';

import {ErrorBoundary} from 'components';
import {formatters, getReportResult} from 'services';
import {t} from 'translation';

import CombinedReportRenderer from './CombinedReportRenderer';
import ProcessReportRenderer from './ProcessReportRenderer';
import DecisionReportRenderer from './DecisionReportRenderer';
import HyperReportRenderer from './HyperReportRenderer';
import SetupNotice from './SetupNotice';
import NoDataNotice from './NoDataNotice';
import ReportErrorNotice from './ReportErrorNotice';

import {isEmpty} from './service';

import './ReportRenderer.scss';

function ReportRenderer(props) {
  const {error, report, updateReport} = props;
  let View, somethingMissing;

  if (report) {
    const result = getReportResult(report);
    const isDecision = report.reportType === 'decision';
    const isHyper = result?.type === 'hyperMap';

    if (report.combined) {
      View = CombinedReportRenderer;
      somethingMissing = checkCombined(report.data);
    } else if (isDecision) {
      View = DecisionReportRenderer;
      somethingMissing = checkDecisionReport(report.data);
    } else {
      View = isHyper ? HyperReportRenderer : ProcessReportRenderer;
      somethingMissing = checkProcessReport(report.data);
    }

    if (somethingMissing) {
      if (updateReport) {
        return <SetupNotice>{somethingMissing}</SetupNotice>;
      } else {
        return <NoDataNotice type="warning">{t('report.incompleteNotice')}</NoDataNotice>;
      }
    }

    if (error) {
      return <ReportErrorNotice error={error} />;
    }

    if (!result) {
      return <NoDataNotice type="info">{t('report.editSetupMessage')}</NoDataNotice>;
    }

    return (
      <ErrorBoundary>
        <div className="ReportRenderer">
          <View {...props} />
          {report.data.configuration.showInstanceCount && (
            <div
              className="additionalInfo"
              dangerouslySetInnerHTML={{
                __html: t(`report.totalCount.${isDecision ? 'evaluation' : 'instance'}`, {
                  count: formatters.frequency(report.result.instanceCount || 0),
                }),
              }}
            />
          )}
        </div>
      </ErrorBoundary>
    );
  }

  if (error) {
    return <ReportErrorNotice error={error} />;
  }

  return <NoDataNotice type="error">{t('report.invalidCombinationError')}</NoDataNotice>;
}

export default React.memo(ReportRenderer, (prevProps, nextProps) => {
  const prevReport = {...prevProps.report, name: ''};
  const nextReport = {...nextProps.report, name: ''};

  if (deepEqual(prevReport, nextReport) && deepEqual(prevProps.error, nextProps.error)) {
    return true;
  }
  return false;
});

function checkCombined(data) {
  const reports = data.reports;
  if (!reports || !reports.length) {
    return t('report.combinedEmptyMessage');
  }
}

function checkDecisionReport(data) {
  if (
    isEmpty(data.definitions) ||
    isEmpty(data.definitions?.[0].key) ||
    isEmpty(data.definitions?.[0].versions)
  ) {
    return <p dangerouslySetInnerHTML={{__html: t('report.noDefinitionMessage.decision')}} />;
  } else {
    return checkSingleReport(data);
  }
}

function checkProcessReport(data) {
  if (
    isEmpty(data.definitions) ||
    isEmpty(data.definitions?.[0].key) ||
    isEmpty(data.definitions?.[0].versions) ||
    isEmpty(data.definitions?.[0].tenantIds)
  ) {
    return <p dangerouslySetInnerHTML={{__html: t('report.noDefinitionMessage.process')}} />;
  } else {
    return checkSingleReport(data);
  }
}

function checkSingleReport(data) {
  if (!data.view) {
    return <p dangerouslySetInnerHTML={{__html: t('report.noViewMessage')}} />;
  } else if (!data.groupBy) {
    return <p dangerouslySetInnerHTML={{__html: t('report.noGroupByMessage')}} />;
  } else if (!data.visualization) {
    return <p dangerouslySetInnerHTML={{__html: t('report.noVisualizationMessage')}} />;
  } else {
    return;
  }
}
