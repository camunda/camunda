import { ApiDefinition, apiGet } from "../request";

export const USERS_ENDPOINT = "/users";

export type User = {
  id: string;
  username: string;
  email: string;
  enabled: boolean;
};

export const getUsers: ApiDefinition<User[]> = () => apiGet(USERS_ENDPOINT);

export type GetUserParams = {
  id: string;
};

export const getUserDetails: ApiDefinition<User, GetUserParams> = ({ id }) =>
  apiGet(`${USERS_ENDPOINT}/${id}`);
