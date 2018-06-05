import React from 'react';
import {ThemeProvider} from 'theme';

import {Dashboard} from './Dashboard';
import {Header} from './Header';

export default function App() {
  return (
    <ThemeProvider>
      <Header />
      <Dashboard />
    </ThemeProvider>
  );
}
