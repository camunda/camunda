/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect, useState} from 'react';

import {Tooltip} from 'components';
import {t} from 'translation';

import './ConnectionStatus.scss';

export default function ConnectionStatus() {
  const [engineStatus, setEngineStatus] = useState({});
  const [connectedToElasticsearch, setConnectedToElasticsearch] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState(false);

  useEffect(() => {
    const {protocol, host, pathname} = window.location;

    const connection = new WebSocket(
      `${protocol === 'https:' ? 'wss' : 'ws'}://${host}${pathname.substring(
        0,
        pathname.lastIndexOf('/')
      )}/ws/status`
    );

    connection.addEventListener('message', ({data}) => {
      const {engineStatus, connectedToElasticsearch} = JSON.parse(data);
      setEngineStatus(engineStatus);
      setConnectedToElasticsearch(connectedToElasticsearch);
      setLoaded(true);
    });

    connection.addEventListener('error', () => {
      setError(true);
    });

    return () => {
      connection.close();
    };
  }, [setConnectedToElasticsearch, setEngineStatus, setError, setLoaded]);

  function renderListElement(key, connectionStatus, isImporting) {
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
  }

  if (!error && !loaded) {
    return null;
  }

  return (
    <div className="ConnectionStatus">
      {error ? (
        <span className="error">{t('footer.connectionError')}</span>
      ) : (
        loaded && (
          <ul className="status">
            <>
              {Object.keys(engineStatus).map((key) =>
                renderListElement(key, engineStatus[key].isConnected, engineStatus[key].isImporting)
              )}
              {renderListElement('Elasticsearch', connectedToElasticsearch, false)}
            </>
          </ul>
        )
      )}
    </div>
  );
}
