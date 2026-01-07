/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { getBaseUrl, getLoginApiUrl, getLogoutApiUrl } from "src/configuration";

let loggedIn = false;

export function activateSession() {
  loggedIn = true;
}

export function disableSession() {
  loggedIn = false;
}

export function isLoggedIn() {
  return loggedIn;
}

export function login(
  username: string,
  password: string,
): Promise<{ success: boolean; message: string }> {
  const data = new FormData();
  data.set("username", username);
  data.set("password", password);
  return fetch(getLoginApiUrl(), {
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
  return fetch(getLogoutApiUrl(), {
    method: "post",
    body: data,
    mode: "no-cors",
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
