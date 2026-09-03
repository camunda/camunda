export declare type PropertyPath = (string | number | [string, number])[];
export declare function iteratePath(path: PropertyPath, obj: any, f?: (x: any) => void): any;
