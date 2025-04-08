/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { getBaseUrl } from "src/configuration";

export const LOGIN_PATH = `${getBaseUrl()}/login`;

export function getLoginPath(next?: string) {
  return next ? `${LOGIN_PATH}?next=${next}` : LOGIN_PATH;
}

export function redirectToLogin() {
  window.location.href = getLoginPath(window.location.pathname);
}

export function login(
  username: string,
  password: string,
): Promise<{ success: boolean; message: string }> {
  const data = new FormData();
  data.set("username", username);
  data.set("password", password);
  return fetch("/login", {
    method: "post",
    body: data,
  })
    .then((response: Response) => {
      if (response.status < 400) {
        return { success: true, message: "" };
      }

      if (response.status === 401) {
        return { success: false, message: "Username and password don't match" };
      }

      return {
        success: false,
        message: "An error occurred. Please try again.",
      };
    })
    .catch(() => {
      return {
        success: false,
        message: "An error occurred. Please try again.",
      };
    });
}

export function logout(): Promise<void> {
  const data = new FormData();
  return fetch("/logout", {
    method: "post",
    body: data,
  })
    .then((response: Response) => {
      if (response.status < 400) {
        window.location.href = `${getBaseUrl()}/`;
      }
    })
    .catch((e) => {
      console.log(e);
    });
}
