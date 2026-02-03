/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { getLoginApiUrl, getLogoutApiUrl } from "src/configuration";

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

async function parseRedirectUrl(response: Response): Promise<string> {
  const json = await response.json();
  if (!json || !json.url) {
    throw new Error("no redirect URL supplied");
  }
  return json.url;
}

export async function login(
  username: string,
  password: string,
): Promise<{ success: boolean; message: string }> {
  const data = new FormData();
  data.set("username", username);
  data.set("password", password);
  try {
    let response = await fetch(getLoginApiUrl(), {
      method: "post",
      body: data,
    });
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
  } catch (e) {
    console.error("login failed", e);
    return {
      success: false,
      message: "An error occurred. Please try again.",
    };
  }
}

export async function logout(): Promise<void> {
  try {
    const response = await fetch(getLogoutApiUrl(), {
      method: "post",
    });
    if (response.status >= 400) {
      console.log("Logout failed: ", response);
      return Promise.reject("Logout failed");
    }
    const idpLogoutUrl =
      response.status === 200 ? await parseRedirectUrl(response) : undefined;
    if (idpLogoutUrl) {
      window.location.href = idpLogoutUrl;
    } else {
      window.location.reload();
    }
  } catch (e) {
    console.log(e);
    return Promise.reject("Logout failed");
  }
}
