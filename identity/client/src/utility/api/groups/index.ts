import { ApiDefinition, apiGet } from "../request";

export const GROUPS_ENDPOINT = "/groups";

export type Group = {
  id: string;
  name: string;
};

export const getGroups: ApiDefinition<Group[]> = () => apiGet(GROUPS_ENDPOINT);
