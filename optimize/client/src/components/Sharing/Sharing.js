/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect, useState} from 'react';
import {useLocation, useParams} from 'react-router-dom';
import classnames from 'classnames';
import {Launch} from '@carbon/icons-react';
import {Link} from '@carbon/react';

import {
  ReportRenderer,
  DashboardRenderer,
  Loading,
  ErrorPage,
  EntityName,
  LastModifiedInfo,
  ReportDetails,
  InstanceCount,
  DiagramScrollLock,
  PageTitle,
} from 'components';
import {CamundaLogo} from 'icons';
import {useErrorHandling} from 'hooks';
import {t} from 'translation';
import {track} from 'tracking';

import {evaluateEntity, createLoadReportCallback} from './service';

import './Sharing.scss';

export function Sharing() {
  const [evaluationResult, setEvaluationResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const {mightFail} = useErrorHandling();
  const {type, id} = useParams();
  const {search} = useLocation();

  const performEvaluation = useCallback(
    function (params) {
      mightFail(
        evaluateEntity(id, type, params),
        (evaluationResult) => setEvaluationResult(evaluationResult),
        (error) => {
          setEvaluationResult(error.reportDefinition);
          setError(error);
        },
        () => setLoading(false)
      );
    },
    [mightFail, id, type]
  );

  function getSharingView() {
    if (type === 'report') {
      return (
        <ReportRenderer
          error={error}
          report={evaluationResult}
          context="shared"
          loadReport={performEvaluation}
        />
      );
    } else {
      const params = new URLSearchParams(search);
      const filter = params.get('filter');

      return (
        <DashboardRenderer
          loadTile={createLoadReportCallback(id)}
          tiles={evaluationResult.tiles}
          filter={filter && JSON.parse(filter)}
          addons={[<DiagramScrollLock />]}
          disableNameLink
        />
      );
    }
  }

  function hasValidType(type) {
    return type === 'report' || type === 'dashboard';
  }

  function getEntityUrl() {
    const currentUrl = window.location.href;
    const baseUrl = currentUrl.substring(0, currentUrl.indexOf('#')).replace('external/', '');

    return `${baseUrl}#/${type}/${evaluationResult.id}/`;
  }

  useEffect(() => {
    performEvaluation();
    trackSharedEntity(type, id);
  }, [performEvaluation, id, type]);

  if (loading) {
    return <Loading />;
  }

  if (!evaluationResult || !hasValidType(type)) {
    return <ErrorPage noLink />;
  }

  const params = new URLSearchParams(search);
  const isEmbedded = params.get('mode') === 'embed';
  const isReport = type === 'report';
  const header = params.get('header');
  const showTitle = header !== 'linkOnly';
  const SharingView = getSharingView();

  return (
    <div className={classnames('Sharing', {compact: isEmbedded, report: isReport})}>
      <PageTitle
        pageName={isReport ? t('report.label') : t('dashboard.label')}
        resourceName={evaluationResult.name}
      />
      {header !== 'hidden' && (
        <div className="header">
          <div className="title-container">
            {showTitle && (
              <EntityName
                details={
                  isReport ? (
                    <ReportDetails report={evaluationResult} />
                  ) : (
                    <LastModifiedInfo entity={evaluationResult} />
                  )
                }
                name={evaluationResult.name}
              />
            )}
            {header !== 'titleOnly' && (
              <Link
                href={getEntityUrl()}
                target="_blank"
                rel="noopener noreferrer"
                className="title-button"
              >
                {isEmbedded ? t('common.open') : t('common.sharing.openInOptimize')}
                <Launch />
              </Link>
            )}
          </div>
          {type === 'report' && showTitle && <InstanceCount report={evaluationResult} />}
        </div>
      )}
      <div className="content">
        {SharingView}
        {isEmbedded && (
          <a className="iconLink" href={getEntityUrl()} target="_blank" rel="noopener noreferrer">
            <CamundaLogo width="20" height="20" />
          </a>
        )}
        {isEmbedded && isReport && <DiagramScrollLock />}
      </div>
    </div>
  );
}

export default Sharing;

function trackSharedEntity(entityType, entityId) {
  track(createEventName(entityType), {entityId});
}

function createEventName(entityType) {
  return 'viewShared' + entityType.charAt(0).toUpperCase() + entityType.slice(1);
}
