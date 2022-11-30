/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {requestAndParse} from 'modules/request';

type PermissionsDto = Array<'read' | 'write'>;

type UserDto = {
  userId: string;
  displayName: string | null;
  canLogout: boolean;
  permissions?: PermissionsDto;
  roles: ReadonlyArray<string> | null;
  salesPlanType: string | null;
  c8Links: {
    [key in
      | 'console'
      | 'modeler'
      | 'tasklist'
      | 'operate'
      | 'optimize']?: string;
  };
};

const getUser = async (options: Parameters<typeof requestAndParse>[1]) => {
  return requestAndParse<UserDto>({url: '/api/authentications/user'}, options);
};

export {getUser};
export type {UserDto};
