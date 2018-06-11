import {post} from 'request';

export async function loadDashboard() {
  const response = await post('/workflow-instance/count', {
    running: true
  });
  return await response.json();
}
