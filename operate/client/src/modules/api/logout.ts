/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { authenticationStore } from 'modules/stores/authentication';

const logout = async () => {
  try {
    console.log('DEBUG ONLY');
    authenticationStore.expireSession();

    const form = document.createElement('form');
    form.method = 'POST';
    form.action = '/logout';
    form.style.display = 'none';

    // Add CSRF token if needed
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
    if (csrfToken) {
      const csrfInput = document.createElement('input');
      csrfInput.type = 'hidden';
      csrfInput.name = '_csrf';
      csrfInput.value = csrfToken;
      form.appendChild(csrfInput);
    }

    document.body.appendChild(form);
    form.submit();

  } catch (error) {
    console.error('Error during logout:', error);
    window.location.href = '/login';
  }
};

export { logout };
