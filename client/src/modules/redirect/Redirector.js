/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect, useState} from 'react';
import {Redirect} from 'react-router-dom';

let targetState;

export default function Redirector() {
  targetState = useState();

  const [target, setTarget] = targetState;

  useEffect(() => {
    if (target) {
      setTarget();
    }
  });

  if (target) {
    return <Redirect push to={target} />;
  }
  return null;
}

export function redirectTo(path) {
  targetState[1](path);
}
