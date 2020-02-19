/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {createContext, useContext, useState, useEffect} from 'react';

import {get} from 'request';

const UserContext = createContext();

export function UserProvider({children}) {
  const [user, setUser] = useState();

  const refreshUser = async () => {
    const response = await get('api/identity/current/user');
    const user = await response.json();

    setUser(user);
    return user;
  };

  useEffect(() => {
    refreshUser();
  }, []);

  return <UserContext.Provider value={{user, refreshUser}}>{children}</UserContext.Provider>;
}

export default Component => props => <Component {...useContext(UserContext)} {...props} />;
