/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Avatar registry for known users.
 * Images are imported as modules so they work with Vite's asset handling.
 */

import tobiAvatar from './avatars/tobi.jpg';

export type UserAvatar = {
  name: string;
  url: string;
};

/**
 * Registry of known users and their avatar URLs.
 * Add new users here by importing their avatar image and adding to the map.
 */
export const avatarRegistry: Record<string, string> = {
  tobi: tobiAvatar,
  // Add more users here:
  // sebastian: sebastianAvatar,
};

/**
 * Get avatar URL for a user by name (case-insensitive).
 */
export function getAvatarUrl(userName: string): string | undefined {
  const normalizedName = userName.toLowerCase().trim();
  return avatarRegistry[normalizedName];
}

/**
 * Check if a user has an avatar in the registry.
 */
export function hasAvatar(userName: string): boolean {
  return getAvatarUrl(userName) !== undefined;
}

/**
 * Get all known user names with avatars.
 */
export function getKnownUsers(): string[] {
  return Object.keys(avatarRegistry);
}
