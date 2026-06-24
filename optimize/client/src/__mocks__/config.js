/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export const isEmailEnabled = () => true;
export const isSharingEnabled = () => true;
export const getOptimizeVersion = () => '2.7.0';
export const getHeader = () => ({
  textColor: 'dark', // or "light"
  backgroundColor: '#FFFFFF',
  logo: '',
});
export const ConfigProvider = () => <div />;
export {default as newReport} from '../modules/newReport.json';
