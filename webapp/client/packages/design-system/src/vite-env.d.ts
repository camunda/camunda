/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

declare module '*?raw' {
  const content: string;
  export default content;
}

// Side-effect imports of stylesheets from `index.{shadcn,carbon}.ts`. Vite
// processes these via PostCSS / Sass at build time; TS just needs to know
// they're importable as modules.
declare module '*.css';
declare module '*.scss';
