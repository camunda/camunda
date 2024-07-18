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
  roleName: Role["name"];
};

export const assignUserRole: ApiDefinition<undefined, AssignUserRoleParams> = ({
  id,
  roleName,
}) => apiPost(path(id, "roles"), { roleName: roleName });

export type RemoveUserRoleParams = GetUserRolesParams & {
  roleName: Role["name"];
};

export const removeUserRole: ApiDefinition<undefined, RemoveUserRoleParams> = ({
  id,
  roleName,
}) => apiDelete(path(id, "roles", roleName));
