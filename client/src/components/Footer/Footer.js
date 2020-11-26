/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {getOptimizeVersion} from 'config';
import {t} from 'translation';
import {Tooltip} from 'components';

import './Footer.scss';

export default class Footer extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      loaded: false,
      error: false,
      engineStatus: {},
      connectedToElasticsearch: false,
      optimizeVersion: null,
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
      this.setState({loaded: true});
      this.setState(JSON.parse(data));
    });

    this.connection.addEventListener('error', () => {
      this.setState({error: true});
    });

    this.setState({
      optimizeVersion: await getOptimizeVersion(),
    });
  }

  componentWillUnmount() {
    this.connection.close();
  }

  renderListElement = (key, connectionStatus, isImporting) => {
    let className = 'statusItem';
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
      <Tooltip key={key} content={title} align="left">
        <li className={className}>{key}</li>
      </Tooltip>
    );
  };

  render() {
    const {engineStatus, connectedToElasticsearch, optimizeVersion, loaded, error} = this.state;

    const timezoneInfo =
      t('footer.timezone') + ' ' + Intl.DateTimeFormat().resolvedOptions().timeZone;

    return (
      <footer className="Footer">
        <div className="content">
          {error ? (
            <span className="error">{t('footer.connectionError')}</span>
          ) : (
            loaded && (
              <ul className="status">
                <>
                  {Object.keys(engineStatus).map((key) =>
                    this.renderListElement(
                      key,
                      engineStatus[key].isConnected,
                      engineStatus[key].isImporting
                    )
                  )}
                  {this.renderListElement('Elasticsearch', connectedToElasticsearch, false)}
                </>
              </ul>
            )
          )}
          <Tooltip content={timezoneInfo} overflowOnly>
            <div className="timezone">{timezoneInfo}</div>
          </Tooltip>
          <div className="colophon">
            © Camunda Services GmbH {new Date().getFullYear()}, {t('footer.rightsReserved')} |{' '}
            {optimizeVersion}
          </div>
        </div>
      </footer>
    );
  }
}
