/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ReactNode, createContext, useState, useEffect, ComponentType} from 'react';
import {get} from 'request';
import {useUser} from 'hooks';

export interface User {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  roles: string[];
  type: string;
  name: string;
  authorizations: string[];
}

export interface WithUserProps {
  user?: User;
  getUser?: () => Promise<User>;
  refreshUser?: () => Promise<User>;
}

export const UserContext = createContext<WithUserProps>({});
const resolveWithUser: ((user: User) => void)[] = [];

export function UserProvider({children}: {children: ReactNode}): JSX.Element {
  const [user, setUser] = useState<User>();

  const refreshUser = async () => {
    const response = await get('api/identity/current/user');
    const user = await response.json();

    resolveWithUser.forEach((resolve) => resolve(user));
    resolveWithUser.length = 0;

    setUser(user);
    return user;
  };

  const getUser = (): Promise<User> =>
    user ? Promise.resolve(user) : new Promise((resolve) => resolveWithUser.push(resolve));

  useEffect(() => {
    refreshUser();
  }, []);

  return (
    <UserContext.Provider value={{user, refreshUser, getUser}}>{children}</UserContext.Provider>
  );
}

export default function withuser<T extends object>(Component: ComponentType<T>) {
  return (props: Omit<T, keyof WithUserProps> & Partial<WithUserProps>) => {
    return <Component {...useUser()} {...(props as T)} />;
  };
}
