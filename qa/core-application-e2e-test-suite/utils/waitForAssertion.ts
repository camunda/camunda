/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

async function waitForAssertion(options: {
  assertion: () => Promise<void>;
  onFailure: () => Promise<void>;
  maxRetries?: number;
}) {
  const {assertion, onFailure: fallback, maxRetries = 3} = options;
  let retries = 1;

  while (retries < maxRetries) {
    try {
      await assertion();
      break;
    } catch (error) {
      if (retries === maxRetries) {
        throw error;
      }
      console.log(
        `Assertion failed, retrying (attempt ${retries}/${maxRetries})`,
      );
      await fallback();
      retries++;
    }
  }
}

export {waitForAssertion};
