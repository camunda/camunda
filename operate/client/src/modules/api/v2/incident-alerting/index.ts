/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {requestAndParse} from 'modules/request';

type Alert = {
  filters: [
    {
      processDefinitionKey: string;
    },
  ];
  channel: {
    type: string;
    value: string;
  };
};

const fetchAlerts = async () => {
  return requestAndParse<Alert[]>({
    method: 'GET',
    url: `/v2/incident-alerting`,
  });
};

const setAlert = async ({
  processDefinitionKey,
  email,
}: {
  processDefinitionKey: string;
  email: string;
}) => {
  return requestAndParse({
    url: `/v2/incident-alerting/config`,
    method: 'POST',
    body: {
      filters: [{processDefinitionKey}],
      channel: {type: 'email', value: email},
    },
  });
};

export {fetchAlerts, setAlert};
export type {Alert};
