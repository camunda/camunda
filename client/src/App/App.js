import React from 'react';
import {ThemeProvider} from 'theme';

import {Dashboard} from './Dashboard';

export default function App() {
  return (
    <ThemeProvider>
      <Dashboard />
    </ThemeProvider>
  );
}
