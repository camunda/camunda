export const Paths = {
  login() {
    return '/login';
  },
  forbidden() {
    return '/forbidden';
  },
  mappings() {
    return '/mappings';
  },
  users() {
    return '/users';
  },
  groups() {
    return '/groups';
  },
  roles() {
    return '/roles';
  },
  tenants() {
    return '/tenants';
  },
  authorizations() {
    return '/authorizations';
  },
} as const;

export const relativizePath = (path: string) => {
  return `.${path}`;
};
