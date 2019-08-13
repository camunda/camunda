/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import './Footer.scss';
import {getOptimizeVersion} from 'services';
import {t} from 'translation';

export default class Footer extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      connectionStatus: {
        engineConnections: {},
        connectedToElasticsearch: true // initial status before we get first data
      },
      isImporting: {},
      optimizeVersion: null
    };
  }

  async componentDidMount() {
    const {protocol, host, pathname} = window.location;

    this.connection = new WebSocket(
      `${protocol === 'https:' ? 'wss' : 'ws'}://${host}${pathname.substring(
        0,
        pathname.lastIndexOf('/')
      )}/ws/status`
    );

    this.connection.addEventListener('message', ({data}) => {
      this.setState(JSON.parse(data));
    });

    this.setState({
      optimizeVersion: await getOptimizeVersion()
    });
  }

  componentWillUnmount() {
    this.connection.close();
  }

  renderListElement = (key, connectionStatus, isImporting) => {
    let className = 'Footer__connect-status-item';
    let title;

    if (connectionStatus) {
      if (isImporting) {
        title = key + ' ' + t('footer.importing');
        className += ' is-in-progress';
      } else {
        className += ' is-connected';
        title = key + ' ' + t('footer.connected');
      }
    } else {
      title = key + ' ' + t('footer.notConnected');
    }

    return (
      <li key={key} className={className} title={title}>
        {key}
      </li>
    );
  };

  render() {
    const {
      isImporting,
      connectionStatus: {engineConnections, connectedToElasticsearch},
      optimizeVersion
    } = this.state;

    return (
      <footer className="Footer">
        <div className="Footer__content">
          <ul className="Footer__connect-status">
            {Object.keys(engineConnections).map(key => {
              return this.renderListElement(key, engineConnections[key], isImporting[key]);
            })}
            {this.renderListElement('Elasticsearch', connectedToElasticsearch, false)}
          </ul>
          <div className="Footer__colophon">
            © Camunda Services GmbH {new Date().getFullYear()}, {t('footer.rightsReserved')} |{' '}
            {optimizeVersion}
          </div>
        </div>
      </footer>
    );
  }
}
