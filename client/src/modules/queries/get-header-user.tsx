/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {gql} from 'apollo-boost';

const GET_HEADER_USER =
  process.env.NODE_ENV === 'test'
    ? gql`
        query GetHeaderUser {
          currentUser {
            firstname
            lastname
          }
        }
      `
    : gql`
        query GetHeaderUser {
          currentUser @client {
            firstname
            lastname
          }
        }
      `;

const mockGetHeaderUser = {
  request: {
    query: GET_HEADER_USER,
  },
  result: {
    data: {
      currentUser: {
        firstname: 'Demo',
        lastname: 'user',
      },
    },
  },
} as const;

export {GET_HEADER_USER, mockGetHeaderUser};
