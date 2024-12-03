import {
  ApiDefinition,
  apiDelete,
  apiPost,
  apiPut,
  pathBuilder,
} from "src/utility/api/request";
import { USERS_ENDPOINT } from "src/utility/api/users/index";
import { Role } from "src/utility/api/roles";
import { SearchResponse } from "src/utility/api";

export type GetUserRolesParams = { userKey: number };

const path = pathBuilder(USERS_ENDPOINT);

export const getUserRoles: ApiDefinition<
  SearchResponse<Role>,
  GetUserRolesParams
> = ({ userKey }) => apiPost(path(userKey, "roles", "search"));

export interface RoleMemberParams {
  userKey: number;
  roleKey: number;
}

function getUserRolePath(params: RoleMemberParams) {
  return path(params.userKey, "roles", params.roleKey);
}

export const assignUserRole: ApiDefinition<undefined, RoleMemberParams> = (
  params,
) => apiPut(getUserRolePath(params));

export const removeUserRole: ApiDefinition<undefined, RoleMemberParams> = (
  params,
) => apiDelete(getUserRolePath(params));
