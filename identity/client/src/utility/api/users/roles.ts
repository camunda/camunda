import {
  ApiDefinition,
  apiDelete,
  apiGet,
  apiPost,
  pathBuilder,
} from "src/utility/api/request";
import { USERS_ENDPOINT } from "src/utility/api/users/index";
import { Role } from "src/utility/api/roles";

export type GetUserRolesParams = { key: number };

const path = pathBuilder(USERS_ENDPOINT);

export const getUserRoles: ApiDefinition<Role[], GetUserRolesParams> = ({
  key,
}) => apiGet(path(key, "roles"));

export type AssignUserRoleParams = GetUserRolesParams & {
  roleId: Role["id"];
};

export const assignUserRole: ApiDefinition<undefined, AssignUserRoleParams> = ({
  key,
  roleId,
}) => apiPost(path(key, "roles"), { roleId: roleId });

export type RemoveUserRoleParams = GetUserRolesParams & {
  roleId: Role["id"];
};

export const removeUserRole: ApiDefinition<undefined, RemoveUserRoleParams> = ({
  key,
  roleId,
}) => apiDelete(path(key, "roles", roleId));
