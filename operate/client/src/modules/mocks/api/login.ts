/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {mockGetRequest, mockPostRequest} from './mockRequest';

/**
 * Mocks the login request together with the GET of the login page that precedes it, which is where
 * the server sends the CSRF token that the POST has to send back. Mocking them together keeps a
 * caller that mocks two logins in a row correct, because every handler answers a single request.
 */
const mockLogin = () => {
  mockGetRequest('/login').withSuccess('');
  return mockPostRequest('/login');
};

export {mockLogin};
