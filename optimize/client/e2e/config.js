/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
