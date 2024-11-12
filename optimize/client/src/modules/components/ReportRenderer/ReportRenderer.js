/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import deepEqual from 'fast-deep-equal';

import {ErrorBoundary} from 'components';
import {formatters, getReportResult} from 'services';
import {t} from 'translation';

import ProcessReportRenderer from './ProcessReportRenderer';
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
    const isHyper = result?.type === 'hyperMap';

    View = isHyper ? HyperReportRenderer : ProcessReportRenderer;
    somethingMissing = checkProcessReport(report.data);

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
            <div className="additionalInfo">
              {t('report.totalCount.instance', {
                count: formatters.frequency(
                  report.result.instanceCount || 0,
                  report.data.configuration.precision
                ),
              })}
            </div>
          )}
        </div>
      </ErrorBoundary>
    );
  }

  if (error) {
    const hasOpensearchError = error?.detailedMessage?.includes('No interpreter registered');

    if (hasOpensearchError) {
      return (
        <ReportErrorNotice
          error={{
            ...error,
            title: t('apiErrors.reportNotSupportedForOpenSearch'),
            message: null,
          }}
        />
      );
    }

    return <ReportErrorNotice error={error} />;
  }

  return <NoDataNotice type="error">{t('report.invalidCombinationError')}</NoDataNotice>;
}

export default React.memo(ReportRenderer, (prevProps, nextProps) => {
  const prevReport = {...prevProps.report, name: ''};
  const nextReport = {...nextProps.report, name: ''};

  if (
    deepEqual(prevReport, nextReport) &&
    deepEqual(prevProps.error, nextProps.error) &&
    prevProps.loading === nextProps.loading
  ) {
    return true;
  }
  return false;
});

function checkProcessReport(data) {
  if (
    isEmpty(data.definitions) ||
    isEmpty(data.definitions?.[0].key) ||
    isEmpty(data.definitions?.[0].versions) ||
    isEmpty(data.definitions?.[0].tenantIds)
  ) {
    return <p>{t('report.noDefinitionMessage.process')}</p>;
  } else {
    return checkSingleReport(data);
  }
}

function checkSingleReport(data) {
  if (!data.view) {
    return <p>{t('report.noViewMessage')}</p>;
  } else if (!data.groupBy) {
    return <p>{t('report.noGroupByMessage')}</p>;
  } else if (!data.visualization) {
    return <p>{t('report.noVisualizationMessage')}</p>;
  } else {
    return;
  }
}
