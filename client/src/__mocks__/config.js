/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export const isEmailEnabled = () => true;
export const isSharingEnabled = () => true;
export const getOptimizeVersion = () => '2.7.0';
export const getWebappEndpoints = () => ({
  default: {endpoint: 'http://localhost:8080/camunda', engineName: 'default'},
});
export const getHeader = () => ({
  textColor: 'dark', // or "light"
  backgroundColor: '#FFFFFF',
  logo: '',
});
export {default as newReport} from '../modules/newReport.json';
