/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {requestWithThrow} from 'modules/request';

export type UpsertCommentRequest = {
  id: string;
  comment: string;
};

export type UpsertCommentResponse = {
  id: string;
  comment: string;
};

const upsertComment = async (request: UpsertCommentRequest) => {
  return requestWithThrow<UpsertCommentResponse>({
    url: `/api/v2/audit-log`,
    method: 'POST',
    body: request,
  });
};

export {upsertComment};
