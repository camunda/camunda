/**
 * This file exists only to avoid circular dependencies. Do not export it directly.
 */

import {z} from 'zod';

const groupSchema = z.object({
	groupId: z.string(),
	name: z.string(),
	description: z.string(),
});
type Group = z.infer<typeof groupSchema>;

const roleSchema = z.object({
	roleId: z.string(),
	name: z.string(),
	description: z.string(),
});
type Role = z.infer<typeof roleSchema>;

export {groupSchema, roleSchema};
export type {Group, Role};
