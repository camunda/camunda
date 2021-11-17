/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {withRouter} from 'react-router-dom';
import {trackPageView} from './tracking';

class PageViewTracker extends React.Component {
  componentDidMount() {
    // log initial page load
    trackPageView();
  }

  componentWillUpdate({location, history}) {
    if (location.pathname === this.props.location.pathname) {
      // don't log identical link clicks (nav links likely)
      return;
    }

    if (history.action === 'PUSH') {
      trackPageView();
    }
  }

  render() {
    return null;
  }
}

export default withRouter(PageViewTracker);
