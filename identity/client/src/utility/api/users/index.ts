import {ApiDefinition, apiGet, apiPost, apiPut} from "../request";

export const USERS_ENDPOINT = "/users";

export type User = {
  id: string;
  username: string;
  password: string;
  email: string;
  enabled: boolean;
};

export const getUsers: ApiDefinition<User[]> = () => apiGet(USERS_ENDPOINT);

type GetUserParams = {
  id: string;
};

export const getUserDetails: ApiDefinition<User, GetUserParams> = ({ id }) =>
  apiGet(`${USERS_ENDPOINT}/${id}`);

type CreateUserParams = Omit<User, "id" | "enabled">;

export const createUser: ApiDefinition<undefined, CreateUserParams> = (user) =>
    apiPost(USERS_ENDPOINT, {...user, enabled: true});

type UpdateUserParams = Omit<User, "enabled">;

export const updateUser: ApiDefinition<undefined, UpdateUserParams> = (user) =>
    apiPut(`${USERS_ENDPOINT}/${user.id}`, {...user, enabled: true});
