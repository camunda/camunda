import {z} from 'zod';
import {API_VERSION, type Endpoint} from './common';

const licenseSchema = z.object({
	validLicense: z.boolean(),
	licenseType: z.string(),
	isCommercial: z.boolean(),
	expiresAt: z.string().nullable(),
});

type License = z.infer<typeof licenseSchema>;

const getLicense: Endpoint = {
	method: 'GET',
	getUrl: () => `/${API_VERSION}/license`,
};

export {licenseSchema, getLicense};
export type {License};
