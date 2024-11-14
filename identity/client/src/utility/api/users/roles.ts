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
  roleId: Role["key"];
};

export const assignUserRole: ApiDefinition<undefined, AssignUserRoleParams> = ({
  id,
  roleId,
}) => apiPost(path(id, "roles"), { roleId: roleId });

export type RemoveUserRoleParams = GetUserRolesParams & {
  roleId: Role["key"];
};

export const removeUserRole: ApiDefinition<undefined, RemoveUserRoleParams> = ({
  id,
  roleId,
}) => apiDelete(path(id, "roles", roleId));
