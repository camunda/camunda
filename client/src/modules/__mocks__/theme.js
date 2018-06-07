import React from 'react';

export {default as Colors} from '../theme/colors.js';
export {default as operateTheme} from '../theme/operate-theme';

export const themed = StyledComponent => props => (
  <StyledComponent theme="dark" {...props} />
);

export const themeStyle = config => ({theme}) => config[theme];
