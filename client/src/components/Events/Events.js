/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Route, Switch} from 'react-router-dom';

import {SubNav, ErrorPage} from 'components';
import {t} from 'translation';

import EventsProcesses from './EventsProcesses';
import IngestedEvents from './IngestedEvents';

import './Events.scss';

export default function Events() {
  return (
    <div className="Events">
      <SubNav>
        <SubNav.Item
          name={t('events.label')}
          linksTo="/eventBasedProcess/"
          active="/eventBasedProcess"
        />
        <SubNav.Item
          name={t('events.ingested.eventSources')}
          linksTo="/ingestedEvents/"
          active="/ingestedEvents"
        />
      </SubNav>
      <Switch>
        <Route path="/eventBasedProcess/" exact component={EventsProcesses} />
        <Route path="/ingestedEvents" component={IngestedEvents} />
        <Route path="*" component={() => <ErrorPage noLink />} />
      </Switch>
    </div>
  );
}
