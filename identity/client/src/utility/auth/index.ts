/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { z } from "zod";
import {
  getBaseUrl,
  getLoginApiUrl,
  getLogoutApiUrl,
} from "../../configuration/urlConfig";

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

const logoutResponseSchema = z.object({
  url: z.url({ message: "No redirect URL provided" }),
});

async function parseRedirectUrl(response: Response): Promise<string> {
  const json = await response.json();
  const result = logoutResponseSchema.parse(json);
  return result.url;
}

/**
 * Gets a CSRF token from the login page and keeps it for the requests that follow. The login
 * endpoint rejects a POST without a token, and a GET of the login page is where the server sends
 * one.
 */
async function requestLoginCsrfToken(): Promise<string | null> {
  try {
    const response = await fetch(getLoginApiUrl(), {
      method: "get",
      headers: { Accept: "text/html" },
    });
    const csrfToken = response.headers.get("X-CSRF-TOKEN");

    if (csrfToken !== null) {
      sessionStorage.setItem("X-CSRF-TOKEN", csrfToken);
    }

    return csrfToken;
  } catch (e) {
    // The login request that follows reports the failure to the user as a rejected login. Log the
    // cause so a missing CSRF token is distinguishable from bad credentials.
    console.error("fetching a CSRF token failed", e);
    return null;
  }
}

export async function login(
  username: string,
  password: string,
): Promise<{ success: boolean; message: string }> {
  const data = new FormData();
  data.set("username", username);
  data.set("password", password);
  try {
    const csrfToken = await requestLoginCsrfToken();
    let response = await fetch(getLoginApiUrl(), {
      method: "post",
      body: data,
      headers: csrfToken === null ? undefined : { "X-CSRF-TOKEN": csrfToken },
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
      window.location.href = `${getBaseUrl()}/`;
    }
  } catch (e) {
    console.log(e);
    return Promise.reject("Logout failed");
  }
}
