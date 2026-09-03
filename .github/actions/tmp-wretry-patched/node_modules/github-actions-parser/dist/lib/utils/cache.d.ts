export declare class TTLCache {
    private defaultTTLinMS;
    private cache;
    constructor(defaultTTLinMS?: number);
    /**
     *
     * @param key Key to cache value under
     * @param ttlInMS How long is the content valid. If optional, default value will be used
     * @param getter Function to retrieve content if not in cache
     */
    get<T>(key: string, ttlInMS: number | undefined, getter: () => Promise<T>): Promise<T>;
}
