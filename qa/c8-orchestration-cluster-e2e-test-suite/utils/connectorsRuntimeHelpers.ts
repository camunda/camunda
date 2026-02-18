/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const CONNECTORS_HEALTH_URL =
  process.env.CONNECTORS_URL || 'http://localhost:8081';

export async function waitForConnectorsRuntime(
  maxRetries = 30,
  retryDelay = 2000
): Promise<void> {
  let lastError: string | undefined;

  for (let i = 0; i < maxRetries; i++) {
    try {
      const response = await fetch(
        `${CONNECTORS_HEALTH_URL}/actuator/health/readiness`
      );

      if (response.ok) {
        const health = await response.json();
        if (health.status === 'UP') {
          console.log('✓ Connectors runtime is ready');
          return;
        }
        lastError = `Health status is '${health.status}', expected 'UP'`;
      } else {
        const errorBody = await response.text();
        lastError = `Health check failed with status ${response.status}. Response: ${errorBody}`;
      }
    } catch (error) {
      lastError =
        error instanceof Error ? error.message : 'Unknown error occurred';
    }

    await new Promise((resolve) => setTimeout(resolve, retryDelay));
  }

  throw new Error(
    `Connectors runtime failed to become ready after ${maxRetries} attempts. ` +
      (lastError ? `Last error: ${lastError}. ` : '') +
      `Health endpoint: ${CONNECTORS_HEALTH_URL}/actuator/health/readiness. ` +
      'Please ensure connector service is running:\n' +
      'docker compose up -d connectors'
  );
}

export async function validateConnectorsHealth(): Promise<void> {
  console.log(
    `Verifying connectors runtime is healthy at ${CONNECTORS_HEALTH_URL}...`
  );
  await waitForConnectorsRuntime();
}
