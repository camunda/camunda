import {includes} from 'view-utils';

export function parseParams(params) {
  return params
    .slice(1)
    .split('&')
    .reduce((params, part) => {
      const [name, value] = part.split('=');

      return {
        ...params,
        [name]: value
      };
    }, {});
}

export function stringifyParams(params, excluded = []) {
  return Object
    .keys(params)
    .filter(name => !includes(excluded, name))
    .reduce((search, name) => {
      const value = params[name];

      return `${search === '' ? '' : search + '&'}${name}=${value}`;
    }, '');
}
