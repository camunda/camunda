/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const config = {
  endpoint: 'http://localhost:3000',
  collectionsEndpoint: 'http://localhost:3000/#/collections',
  elasticSearchEndpoint: 'http://localhost:9200',
  keycloak: {
    endpoint: 'http://localhost:18080/auth',
    username: 'admin',
    password: 'admin',
    client_id: 'admin-cli',
  },
};

export default config;
