import {TasklistProcessesPage} from '#/tasklist/pages/TasklistProcessesPage';
import {createFileRoute} from '@tanstack/react-router';

export const Route = createFileRoute('/_auth/tasklist/processes')({
	component: TasklistProcessesPage,
});
