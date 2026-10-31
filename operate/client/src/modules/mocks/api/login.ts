/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {mockGetRequest, mockPostRequest} from './mockRequest';

const mockLogin = () => mockPostRequest('/login');

// The login POST is preceded by a GET of the login page, which is where the server sends the CSRF
// token that the POST has to send back.
const mockLoginCsrfToken = () => mockGetRequest('/login');

export {mockLogin, mockLoginCsrfToken};
