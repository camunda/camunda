/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {createContext, useContext, useState, useEffect} from 'react';

import {get} from 'request';

const UserContext = createContext();
const resolveWithUser = [];

export function UserProvider({children}) {
  const [user, setUser] = useState();

  const refreshUser = async () => {
    const response = await get('api/identity/current/user');
    const user = await response.json();

    resolveWithUser.forEach((resolve) => resolve(user));
    resolveWithUser.length = 0;

    setUser(user);
    return user;
  };

  const getUser = () => (user ? user : new Promise((resolve) => resolveWithUser.push(resolve)));

  useEffect(() => {
    refreshUser();
  }, []);

  return (
    <UserContext.Provider value={{user, refreshUser, getUser}}>{children}</UserContext.Provider>
  );
}

export default function withUser(Component) {
  return (props) => <Component {...useContext(UserContext)} {...props} />;
}
