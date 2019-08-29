/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import moment from 'moment';
import {Link} from 'react-router-dom';

import {t} from 'translation';

import CreateNewButton from './CreateNewButton';
import ListItem from './ListItem';

import {ReactComponent as ReportIcon} from './icons/report.svg';
import {ReactComponent as DashboardIcon} from './icons/dashboard.svg';
import {ReactComponent as CollectionIcon} from './icons/collection.svg';

import {loadEntities} from './service';

import './Home.scss';

export default class Home extends React.Component {
  state = {
    entities: []
  };

  async componentDidMount() {
    this.setState({
      entities: await loadEntities()
    });
  }

  render() {
    return (
      <div className="Home">
        <div className="header">
          <h1>{t('home.title')}</h1>
          <CreateNewButton />
        </div>
        <ul>
          {this.state.entities.map(
            ({id, entityType, lastModified, name, data, reportType, combined}) => (
              <ListItem key={id} className={entityType}>
                <Link to={formatLink(id, entityType)}>
                  <ListItem.Section className="icon">{getEntityIcon(entityType)}</ListItem.Section>
                  <ListItem.Section className="name">
                    <div className="type">{formatType(entityType, reportType, combined)}</div>
                    <div className="entityName">{name}</div>
                  </ListItem.Section>
                  <ListItem.Section className="containedEntities">
                    {formatSubEntities(data.subEntityCounts)}
                  </ListItem.Section>
                  <ListItem.Section className="modifiedDate">
                    {moment(lastModified).format('YYYY-MM-DD HH:mm')}
                  </ListItem.Section>
                  <ListItem.Section className="users">
                    {formatUserCount(data.roleCounts)}
                  </ListItem.Section>
                </Link>
              </ListItem>
            )
          )}
        </ul>
      </div>
    );
  }
}

function formatLink(id, type) {
  return `/${type}/${id}`;
}

function getEntityIcon(type) {
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

function formatSubEntities({dashboard, report}) {
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

function formatUserCount({user, group}) {
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
    string += t('common.user.' + (group > 1 ? 'label-plural' : 'label'));
  }

  return string;
}

function formatType(entityType, reportType, combined) {
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
