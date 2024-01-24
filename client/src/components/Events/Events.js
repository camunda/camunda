/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Link, Route, Switch, useLocation} from 'react-router-dom';
import {Tab, TabList, Tabs} from '@carbon/react';

import {ErrorPage} from 'components';
import {t} from 'translation';

import EventsProcesses from './EventsProcesses';
import IngestedEvents from './IngestedEvents';

import './Events.scss';

export default function Events() {
  const {pathname} = useLocation();

  return (
    <div className="Events">
      <Tabs selectedIndex={pathname.includes('events/processes') ? 0 : 1}>
        <TabList aria-label="tabs" className="tabList">
          <Tab as={Link} to="/events/processes/">
            {t('events.label')}
          </Tab>
          <Tab as={Link} to="/events/ingested/">
            {t('events.ingested.eventSources')}
          </Tab>
        </TabList>
      </Tabs>
      <Switch>
        <Route path="/events/processes/" exact component={EventsProcesses} />
        <Route path="/events/ingested/" component={IngestedEvents} />
        <Route path="*" component={() => <ErrorPage noLink />} />
      </Switch>
    </div>
  );
}
