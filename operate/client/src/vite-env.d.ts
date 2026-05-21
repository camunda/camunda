/* eslint-disable license-header/header */

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/// <reference types="vite/client" />
/// <reference types="vite-plugin-svgr/client" />

interface ImportMetaEnv {
  readonly VITE_VERSION: string;
  readonly VITE_DEV_ENV_URL: string;
  readonly VITE_INT_ENV_URL: string;
  readonly VITE_PROD_ENV_URL: string;
  readonly VITE_MIXPANEL_TOKEN: string;
  readonly VITE_MIXPANEL_HOST: string;
  readonly VITE_OSANO_INT_ENV_URL: string;
  readonly VITE_OSANO_PROD_ENV_URL: string;
  readonly VITE_CUES_HOST: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
