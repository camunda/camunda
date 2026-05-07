/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {endpoints} from '@camunda/camunda-api-zod-schemas/8.10';
import {request} from 'modules/request';

const fetchDocument = async ({
  documentId,
  storeId,
  contentHash,
}: {
  documentId: string;
  storeId?: string;
  contentHash?: string;
}) => {
  return request({
    url: endpoints.getDocument.getUrl({documentId, storeId, contentHash}),
    method: endpoints.getDocument.method,
  });
};

export {fetchDocument};
