/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { ApiError } from "./request";

export type DispatchOptions = { skipToast: boolean };
export type ErrorNotifier = (error: ApiError, options: DispatchOptions) => void;

let dispatch: ErrorNotifier = () => {};

export function setErrorNotifier(notifier: ErrorNotifier) {
  dispatch = notifier;
}

export function notifyApiError(error: unknown, options: DispatchOptions) {
  if (error instanceof ApiError) {
    dispatch(error, options);
  }
}
