/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Link, Route, Switch, useLocation} from 'react-router-dom';

import {ErrorPage, Tabs} from 'components';
import {t} from 'translation';

import EventsProcesses from './EventsProcesses';
import IngestedEvents from './IngestedEvents';

import './Events.scss';

export default function Events() {
  const {pathname} = useLocation();

  return (
    <div className="Events">
      <Switch>
        <Tabs value={getTabValue(pathname)}>
          <Tabs.Tab as={Link} to="/events/processes/" title={t('events.label')}>
            <Route path="/events/processes/" exact component={EventsProcesses} />
          </Tabs.Tab>
          <Tabs.Tab as={Link} to="/events/ingested/" title={t('events.ingested.eventSources')}>
            <Route path="/events/ingested/" component={IngestedEvents} />
          </Tabs.Tab>
        </Tabs>
        <Tabs.Tab hidden>
          <Route path="*" component={() => <ErrorPage noLink />} />
        </Tabs.Tab>
      </Switch>
    </div>
  );
}

function getTabValue(pathname) {
  if (!!pathname.match(/\/events\/processes(\/?)$/)) {
    return 0;
  }
  if (!!pathname.match(/\/events\/ingested/)) {
    return 1;
  }
  return 2;
}
