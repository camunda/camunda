/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export const cleanServiceWorkers = async () => {
  try {
    if ("serviceWorker" in navigator) {
      const registrations = await navigator.serviceWorker.getRegistrations();

      for (let registration of registrations) {
        console.log(
          "Found service worker with scriptUrl:",
          registration.active?.scriptURL,
          "Assuming this is leftover from a previous Management Identity deployment and removing it...",
        );
        await registration.unregister();
      }
    }
  } catch (e) {
    console.error("Error occurred during cleanup of service workers", e);
  }
};
