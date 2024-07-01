import {Outlet, useRouteError} from '@remix-run/react';
import {NetworkStatusWatcher} from 'NetworkStatusWatcher';
import {SessionWatcher} from 'SessionWatcher';
import {FallbackErrorPage} from 'errorBoundaries';
import {Notifications} from 'modules/notifications';
import {ReactQueryProvider} from 'modules/react-query/ReactQueryProvider';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {TrackPagination} from 'modules/tracking/TrackPagination';

const GlobalLayout: React.FC = () => {
  return (
    <>
      <SessionWatcher />
      <TrackPagination />
      <ThemeProvider>
        <ReactQueryProvider>
          <Notifications />
          <NetworkStatusWatcher />
          <Outlet />
        </ReactQueryProvider>
      </ThemeProvider>
    </>
  );
};

export const ErrorBoundary: React.FC = () => {
  const error = useRouteError();
  return <FallbackErrorPage error={error} />;
};

export default GlobalLayout;
