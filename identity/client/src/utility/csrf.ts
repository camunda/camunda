/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import Cookies from "js-cookie";

export const CSRF_REQUEST_PARAMETER = "_csrf";
export const CSRF_REQUEST_HEADER = "X-CSRF-Token";
const CSRF_TOKEN_COOKIE = "XSRF-TOKEN";

export function getCsrfToken(): string {
  return Cookies.get(CSRF_TOKEN_COOKIE) ?? "";
}
