/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {t} from 'translation';

import {ReactComponent as ReportIcon} from './icons/report.svg';
import {ReactComponent as DashboardIcon} from './icons/dashboard.svg';
import {ReactComponent as CollectionIcon} from './icons/collection.svg';

export function formatLink(id, type) {
  return `${type}/${id}/`;
}

export function getEntityIcon(type) {
  switch (type) {
    case 'collection':
      return <CollectionIcon />;
    case 'dashboard':
      return <DashboardIcon />;
    case 'report':
      return <ReportIcon />;
    default:
      return <ReportIcon />;
  }
}

export function formatType(entityType, reportType, combined) {
  switch (entityType) {
    case 'collection':
      return t('common.collection.label');
    case 'dashboard':
      return t('dashboard.label');
    case 'report':
      if (reportType === 'process' && !combined) {
        return t('home.types.process');
      }
      if (reportType === 'process' && combined) {
        return t('home.types.combined');
      }
      if (reportType === 'decision') {
        return t('home.types.decision');
      }
      return t('report.label');
    default:
      return t('home.types.unknown');
  }
}

export function formatSubEntities({dashboard, report}) {
  let string = '';
  if (dashboard) {
    string += dashboard + ' ';
    string += t('dashboard.' + (dashboard > 1 ? 'label-plural' : 'label'));
    if (report) {
      string += ', ';
    }
  }
  if (report) {
    string += report + ' ';
    string += t('report.' + (report > 1 ? 'label-plural' : 'label'));
  }

  return string;
}

export function formatUserCount({user, group}) {
  let string = '';
  if (group) {
    string += group + ' ';
    string += t('common.user-group.' + (group > 1 ? 'label-plural' : 'label'));
    if (user) {
      string += ', ';
    }
  }
  if (user) {
    string += user + ' ';
    string += t('common.user.' + (user > 1 ? 'label-plural' : 'label'));
  }

  return string;
}
