/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {Tooltip} from 'components';
import {t} from 'translation';

import './ConnectionStatus.scss';

interface EngineStatus {
  [key: string]: {
    isConnected: boolean;
    isImporting: boolean;
  };
}

export default function ConnectionStatus() {
  const [engineStatus, setEngineStatus] = useState<EngineStatus>({});
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
  }, []);

  function renderListElement(key: string, connectionStatus: boolean, isImporting: boolean) {
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
    <ul className="ConnectionStatus">
      {error ? (
        <li className="error">{t('footer.connectionError')}</li>
      ) : (
        loaded && (
          <>
            {Object.entries(engineStatus).map(([key, {isConnected, isImporting}]) =>
              renderListElement(key, isConnected, isImporting)
            )}
            {renderListElement(t('footer.database').toString(), connectedToElasticsearch, false)}
          </>
        )
      )}
    </ul>
  );
}
