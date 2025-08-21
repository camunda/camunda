import {z} from 'zod';
import {API_VERSION, type Endpoint} from './common';

const currentUserSchema = z.object({
	username: z.string(),
	displayName: z.string(),
	email: z.string(),
	authorizedComponents: z.array(z.string()),
	tenants: z.array(
		z.object({
			key: z.number(),
			tenantId: z.string(),
			name: z.string(),
			description: z.string().optional(),
		}),
	),
	groups: z.array(z.string()),
	roles: z.array(z.string()),
	salesPlanType: z.string().nullable(),
	c8Links: z.array(
		z.object({
			name: z.string(),
			link: z.string(),
		}),
	),
	canLogout: z.boolean(),
	apiUser: z.boolean().optional(),
});

type CurrentUser = z.infer<typeof currentUserSchema>;

const getCurrentUser: Endpoint = {
	method: 'GET',
	getUrl: () => `/${API_VERSION}/authentication/me`,
};

export {currentUserSchema, getCurrentUser};
export type {CurrentUser};
