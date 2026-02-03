/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { http, HttpResponse } from "msw";
import { isLoggedIn, login, logout } from "src/utility/auth";
import { nodeMockServer } from "../testing/nodeMockServer";

const unchangedHref = window.location.href;

describe("authentication", () => {
  it("should login", async () => {
    // given
    nodeMockServer.use(
      http.post("/login", () => new HttpResponse(""), { once: true }),
    );

    expect(isLoggedIn()).toBe(false);

    // when
    const result = await login("demo", "demo");

    // then
    expect(result.success).toBe(true);
  });

  it("should handle login failure", async () => {
    // given
    nodeMockServer.use(
      http.post("/login", () => new HttpResponse("", { status: 401 }), {
        once: true,
      }),
    );

    expect(await login("demo", "demo")).toStrictEqual({
      success: false,
      message: "Username and password don't match",
    });
  });

  it("should logout with basic auth", async () => {
    // given
    const mockReload = vi.fn();
    vi.spyOn(window, "location", "get").mockReturnValue({
      ...window.location,
      reload: mockReload,
    });

    nodeMockServer.use(
      http.post("/logout", () => new HttpResponse("", { status: 204 }), {
        once: true,
      }),
    );

    // when
    await logout();

    // then
    expect(mockReload).toHaveBeenCalledTimes(1);
    expect(window.location.href).toBe(unchangedHref);
  });

  it("should throw an error on logout failure", async () => {
    // given
    nodeMockServer.use(
      http.post("/logout", () => new HttpResponse("", { status: 500 }), {
        once: true,
      }),
    );

    //expect
    await expect(logout()).rejects.toThrow("Logout failed");
  });

  it("should logout with oidc and rp initiated logout", async () => {
    // given
    const mockReload = vi.fn();
    vi.spyOn(window, "location", "get").mockReturnValue({
      ...window.location,
      reload: mockReload,
    });
    const mockIdpLogoutUrl = "http://example.com/idpLogout";

    nodeMockServer.use(
      http.post(
        "/logout",
        () =>
          new HttpResponse(`{"url": "${mockIdpLogoutUrl}"}`, { status: 200 }),
        {
          once: true,
        },
      ),
    );

    // when
    await logout();

    // then
    expect(mockReload).toHaveBeenCalledTimes(0);
    expect(window.location.href).toBe(mockIdpLogoutUrl);
  });
});
