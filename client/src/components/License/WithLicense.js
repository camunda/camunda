/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect} from 'react';
import {Route} from 'react-router-dom';

import License from './License';

import {addHandler, removeHandler} from 'request';

export default function WithLicense({children}) {
  const [showLicensePage, setShowLicensePage] = useState(false);

  useEffect(() => {
    const handleResponse = async response => {
      if (response.status === 403) {
        try {
          const {errorCode} = await response.clone().json();
          if (errorCode === 'noLicenseStoredError') {
            setShowLicensePage(true);
          }
        } catch (e) {
          // response does not contain an error code, do nothing
        }
      }

      return response;
    };

    addHandler(handleResponse);
    return () => removeHandler(handleResponse);
  }, []);

  return <Route path="/" render={() => (showLicensePage ? <License /> : children)} />;
}
