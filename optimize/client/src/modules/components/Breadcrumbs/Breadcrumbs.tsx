/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {Link, useLocation} from 'react-router-dom';
import {Breadcrumb, BreadcrumbItem, BreadcrumbSkeleton} from '@carbon/react';

import {useErrorHandling} from 'hooks';
import {showError} from 'notifications';
import {t} from 'translation';

import {loadEntitiesNames, EntityNamesResponse, getEntityId} from './service';

import './Breadcrumbs.scss';

export default function Breadcrumbs() {
  const {mightFail} = useErrorHandling();
  const location = useLocation();
  const [entityNames, setEntityNames] = useState<EntityNamesResponse>();
  const collection = getEntityId('collection', location.pathname);
  const dashboard = getEntityId('dashboard', location.pathname);
  const report = getEntityId('report', location.pathname);
  const isInstantDashboard = dashboard === 'instant';
  const isProcessOverviewReport = location.pathname.includes('/processes/');

  useEffect(() => {
    if (!isInstantDashboard && (dashboard || collection)) {
      mightFail(
        loadEntitiesNames({dashboardId: dashboard, collectionId: collection}),
        setEntityNames,
        showError
      );
    } else {
      setEntityNames({
        reportName: null,
        dashboardName: null,
        collectionName: null,
      });
    }
  }, [collection, dashboard, isInstantDashboard, mightFail]);

  const homePageBreadcrumb =
    isProcessOverviewReport || isInstantDashboard ? (
      <BreadcrumbItem>
        <Link to="/">{t('navigation.dashboards')}</Link>
      </BreadcrumbItem>
    ) : (
      <BreadcrumbItem>
        <Link to="/collections">{t('navigation.collections')}</Link>
      </BreadcrumbItem>
    );

  return entityNames ? (
    <Breadcrumb className="Breadcrumbs">
      {homePageBreadcrumb}
      {collection && (
        <BreadcrumbItem>
          <Link to={`/collection/${collection}/`}>{entityNames.collectionName}</Link>
        </BreadcrumbItem>
      )}
      {report && dashboard && (
        <BreadcrumbItem>
          <Link
            to={
              collection
                ? `/collection/${collection}/dashboard/${dashboard}/`
                : `/dashboard/${dashboard}/`
            }
          >
            {entityNames.dashboardName}
          </Link>
        </BreadcrumbItem>
      )}
    </Breadcrumb>
  ) : (
    <BreadcrumbSkeleton />
  );
}
