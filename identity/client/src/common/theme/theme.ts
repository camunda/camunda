/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { action, computed, makeObservable, observable } from "mobx";

type ThemeOption = "system" | "dark" | "light";

const STORAGE_KEY = "theme";

function getStoredTheme(): ThemeOption {
  try {
    const value = localStorage.getItem(STORAGE_KEY);
    if (value !== null) {
      const parsed: unknown = JSON.parse(value);
      if (isThemeOption(parsed)) {
        return parsed;
      }
    }
  } catch {
    // ignore parse errors
  }
  return "system";
}

function isThemeOption(theme: unknown): theme is ThemeOption {
  return ["system", "dark", "light"].includes(theme as string);
}

class Theme {
  selectedTheme: ThemeOption = getStoredTheme();
  #systemDefault: "dark" | "light" = window.matchMedia(
    "(prefers-color-scheme: dark)",
  ).matches
    ? "dark"
    : "light";

  constructor() {
    makeObservable(this, {
      selectedTheme: observable,
      changeTheme: action,
      actualTheme: computed,
    });

    window
      .matchMedia("(prefers-color-scheme: dark)")
      .addEventListener("change", (event) => {
        this.#updateSystemDefault(event.matches ? "dark" : "light");
      });
  }

  changeTheme = (theme: ThemeOption) => {
    this.selectedTheme = theme;
    localStorage.setItem(STORAGE_KEY, JSON.stringify(theme));
  };

  get actualTheme(): "dark" | "light" {
    return this.selectedTheme === "system"
      ? this.#systemDefault
      : this.selectedTheme;
  }

  #updateSystemDefault = action((value: "dark" | "light") => {
    this.#systemDefault = value;
  });
}

const themeStore = new Theme();

export { themeStore, isThemeOption };
export type { ThemeOption };
