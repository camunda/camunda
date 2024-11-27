import {
  ApiDefinition,
  apiDelete,
  apiGet,
  apiPost,
  pathBuilder,
} from "src/utility/api/request";
import { USERS_ENDPOINT } from "src/utility/api/users/index";
import { Role } from "src/utility/api/roles";

export type GetUserRolesParams = { id: string };

const path = pathBuilder(USERS_ENDPOINT);

export const getUserRoles: ApiDefinition<Role[], GetUserRolesParams> = ({
  id,
}) => apiGet(path(id, "roles"));

export type AssignUserRoleParams = GetUserRolesParams & {
  roleKey: Role["key"];
};

export const assignUserRole: ApiDefinition<undefined, AssignUserRoleParams> = ({
  id,
  roleKey,
}) => apiPost(path(id, "roles"), { roleId: roleKey });

export type RemoveUserRoleParams = GetUserRolesParams & {
  roleKey: Role["key"];
};

export const removeUserRole: ApiDefinition<undefined, RemoveUserRoleParams> = ({
  id,
  roleKey,
}) => apiDelete(path(id, "roles", roleKey));
