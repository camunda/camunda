/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {get, del, post} from 'request';
import {getFullURL} from '../../modules/api';

export async function shareReport(reportId) {
  const body = {
    reportId,
  };
  const response = await post(getFullURL(`api/share/report`), body);

  const json = await response.json();
  return json.id;
}

export async function getSharedReport(reportId) {
  const response = await get(getFullURL(`api/share/report/${reportId}`));

  if (response.status > 201) {
    return '';
  } else {
    const json = await response.json();
    return json.id;
  }
}

export async function revokeReportSharing(id) {
  return await del(getFullURL(`api/share/report/${id}`));
}
