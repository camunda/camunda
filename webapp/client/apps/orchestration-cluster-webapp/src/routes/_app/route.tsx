import {TrackPagination} from '../../modules/tracking/TrackPagination';
import {createFileRoute, Outlet} from '@tanstack/react-router';

export const Route = createFileRoute('/_app')({
	component: RouteComponent,
});

function RouteComponent() {
	return (
		<>
			<TrackPagination />
			<Outlet />
		</>
	);
}
