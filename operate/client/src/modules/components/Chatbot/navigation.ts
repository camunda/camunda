/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Navigation service for the Chatbot
 *
 * Allows the chatbot to navigate to different pages within Operate.
 * Uses React Router for client-side navigation to preserve app state.
 */

export type NavigationTarget = {
  type: 'processInstance' | 'processDefinition' | 'incidents' | 'dashboard' | 'processes' | 'operationsLog' | 'batchOperation';
  key?: string;
  params?: Record<string, string>;
};

export type NavigateFunction = (path: string) => void;

/**
 * Parses a navigation command from the LLM response.
 * The LLM can include navigation commands in the format:
 * [NAVIGATE:type:key] or [NAVIGATE:type]
 *
 * Examples:
 * - [NAVIGATE:processInstance:2251799813685319]
 * - [NAVIGATE:incidents]
 * - [NAVIGATE:dashboard]
 */
export function parseNavigationCommand(text: string): NavigationTarget | null {
  const match = text.match(/\[NAVIGATE:(\w+)(?::([^\]]+))?\]/);
  if (!match) {
    return null;
  }

  const [, type, key] = match;

  switch (type) {
    case 'processInstance':
      return key ? {type: 'processInstance', key} : null;
    case 'processDefinition':
      return key ? {type: 'processDefinition', key} : null;
    case 'incidents':
      return {type: 'incidents'};
    case 'dashboard':
      return {type: 'dashboard'};
    case 'processes':
      return {type: 'processes'};
    case 'operationsLog':
      return {type: 'operationsLog'};
    case 'batchOperation':
      return key ? {type: 'batchOperation', key} : null;
    default:
      return null;
  }
}

/**
 * Removes navigation commands from the text for display
 */
export function stripNavigationCommands(text: string): string {
  return text.replace(/\[NAVIGATE:\w+(?::[^\]]+)?\]/g, '').trim();
}

/**
 * Builds a URL path for a navigation target
 */
export function buildNavigationPath(target: NavigationTarget): string {
  switch (target.type) {
    case 'processInstance':
      return `/processes/${target.key}`;
    case 'processDefinition':
      return `/processes?process=${target.key}`;
    case 'incidents':
      return '/processes?incidents=true';
    case 'dashboard':
      return '/';
    case 'processes':
      return '/processes';
    case 'operationsLog':
      return '/operations-log';
    case 'batchOperation':
      return `/batch-operations/${target.key}`;
    default:
      return '/';
  }
}

/**
 * Navigate to a target within Operate using the provided navigate function.
 * This uses React Router's navigate for client-side routing (no page reload).
 */
export function navigateTo(target: NavigationTarget, navigate: NavigateFunction): void {
  const path = buildNavigationPath(target);
  console.log('[Navigation] Navigating to:', path);
  navigate(path);
}

/**
 * Navigate using the path directly
 */
export function navigateToPath(path: string, navigate: NavigateFunction): void {
  console.log('[Navigation] Navigating to path:', path);
  navigate(path);
}
