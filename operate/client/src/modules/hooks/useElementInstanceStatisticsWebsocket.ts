/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQueryClient} from '@tanstack/react-query';
import {useWebsocketContext} from 'modules/websocket/WebSocketProvider';
import {useCallback, useEffect, useState} from 'react';

const useElementInstanceStatisticsWebsocket = (processInstanceKey?: string) => {
  const queryClient = useQueryClient();
  const websocket = useWebsocketContext();

  const [subscriptionId, setSubscriptionId] = useState<string | null>(null);

  const updateMessageHandler = useCallback(
    (event: MessageEvent) => {
      const wsData = JSON.parse(event.data);

      if (
        wsData.type === 'UPDATE' &&
        wsData.subscriptionId === subscriptionId
      ) {
        console.log('update data', wsData.data);

        queryClient.setQueryData(
          ['flownodeInstancesStatistics', processInstanceKey],
          wsData.data,
        );
      }
    },
    [processInstanceKey, queryClient, subscriptionId],
  );

  const subscribeMessageHandler = useCallback((event: MessageEvent) => {
    const data = JSON.parse(event.data);

    if (data.type === 'SUBSCRIBED' && data.subscriptionId) {
      setSubscriptionId(data.subscriptionId);
    }
  }, []);

  useEffect(() => {
    if (websocket === null || processInstanceKey === undefined) {
      return;
    }
    websocket.send(
      JSON.stringify({
        action: 'SUBSCRIBE',
        topic: 'element-instance-statistics',
        parameters: {
          processInstanceKey,
        },
      }),
    );
  }, [websocket, processInstanceKey]);

  useEffect(() => {
    if (websocket === null || processInstanceKey === undefined) {
      return;
    }
    websocket.addEventListener('message', updateMessageHandler);
    websocket.addEventListener('message', subscribeMessageHandler);
    return () => {
      websocket.removeEventListener('message', updateMessageHandler);
      websocket.removeEventListener('message', subscribeMessageHandler);
    };
  }, [
    websocket,
    processInstanceKey,
    updateMessageHandler,
    subscribeMessageHandler,
  ]);
};

export {useElementInstanceStatisticsWebsocket};
