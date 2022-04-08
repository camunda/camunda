/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
          linksTo="/events/processes/"
          active="/events/processes"
        />
        <SubNav.Item
          name={t('events.ingested.eventSources')}
          linksTo="/events/ingested/"
          active="/events/ingested"
        />
      </SubNav>
      <Switch>
        <Route path="/events/processes/" exact component={EventsProcesses} />
        <Route path="/events/ingested/" component={IngestedEvents} />
        <Route path="*" component={() => <ErrorPage noLink />} />
      </Switch>
    </div>
  );
}
