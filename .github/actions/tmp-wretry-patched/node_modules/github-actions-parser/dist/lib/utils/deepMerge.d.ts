/**
 * Simple object check.
 * @param item
 * @returns {boolean}
 */
export declare function isObject(item: any): any;
/**
 * Deep merge two objects.
 * @param target
 * @param ...sources
 */
export declare function mergeDeep<Q extends {}, T = any>(target: Q, ...sources: T[]): any;
