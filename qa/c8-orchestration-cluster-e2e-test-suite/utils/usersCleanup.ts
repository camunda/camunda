import {APIRequestContext} from '@playwright/test';
import {jsonHeaders, buildUrl} from './http';

export async function cleanupUsers(
  request: APIRequestContext,
  usernames: string[],
): Promise<void> {
  if (usernames.length === 0) return;

  console.log(`Cleaning up ${usernames.length} users via API...`);

  await Promise.allSettled(
    usernames.map(async (username) => {
      try {
        const response = await request.delete(
          buildUrl('/users/{username}', {username}),
          {headers: jsonHeaders()},
        );
        if (response.status() === 204) {
          console.log(`Successfully deleted user: ${username}`);
        } else if (response.status() === 404) {
          console.log(`User already deleted or doesn't exist: ${username}`);
        } else {
          console.warn(
            `Unexpected response status ${response.status()} for user ${username}`,
          );
        }
      } catch {}
    }),
  );
}
