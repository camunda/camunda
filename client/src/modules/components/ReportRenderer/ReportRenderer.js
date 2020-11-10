/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import CombinedReportRenderer from './CombinedReportRenderer';
import ProcessReportRenderer from './ProcessReportRenderer';
import DecisionReportRenderer from './DecisionReportRenderer';
import HyperReportRenderer from './HyperReportRenderer';
import SetupNotice from './SetupNotice';
import IncompleteReport from './IncompleteReport';
import NoDataNotice from './NoDataNotice';
import deepEqual from 'deep-equal';

import {ErrorBoundary, MessageBox} from 'components';

import {formatters} from 'services';

import {isEmpty} from './service';

import './ReportRenderer.scss';
import {t} from 'translation';

function ReportRenderer(props) {
  const {report, updateReport, context} = props;
  let View, somethingMissing;
  if (report) {
    const isDecision = report.reportType === 'decision';
    const isHyper = report.result && report.result.type === 'hyperMap';

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
      if (context === 'dashboard' || context === 'shared') {
        return <IncompleteReport id={report.id} />;
      } else if (updateReport) {
        return <SetupNotice>{somethingMissing}</SetupNotice>;
      } else {
        return <SetupNotice dangerouslySetInnerHTML={{__html: t('report.editReportMessage')}} />;
      }
    }

    const showNoDataMessage = !containsData(report);

    return (
      <ErrorBoundary>
        <div className="ReportRenderer">
          {showNoDataMessage ? (
            <NoDataNotice>
              {updateReport && !report.combined && t('report.editSetupMessage')}
            </NoDataNotice>
          ) : (
            <>
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
            </>
          )}
        </div>
      </ErrorBoundary>
    );
  } else {
    return <MessageBox type="error">{t('report.invalidCombinationError')}</MessageBox>;
  }
}

function containsData(report) {
  if (!report.result) {
    return false;
  }
  if (report.combined) {
    return report.data.reports.length > 0 && Object.values(report.result.data).some(containsData);
  } else {
    const {type, instanceCount, data} = report.result;
    if (type && type.toLowerCase().includes('map') && data.length === 0) {
      return false;
    }

    if (type === 'hyperMap' && data.some(({value}) => value.length === 0)) {
      return false;
    }

    return (
      instanceCount ||
      (report.data.view.property === 'frequency' && report.data.visualization === 'number')
    );
  }
}

export default React.memo(ReportRenderer, (prevProps, nextProps) => {
  const prevReport = {...prevProps.report, name: ''};
  const nextReport = {...nextProps.report, name: ''};

  if (deepEqual(prevReport, nextReport)) {
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
  if (isEmpty(data.decisionDefinitionKey) || isEmpty(data.decisionDefinitionVersions)) {
    return <p dangerouslySetInnerHTML={{__html: t('report.noDefinitionMessage.decision')}} />;
  } else {
    return checkSingleReport(data);
  }
}

function checkProcessReport(data) {
  if (isEmpty(data.processDefinitionKey) || isEmpty(data.processDefinitionVersions)) {
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
