export declare function run(cmd: string, args: string[], opts?: {
    inherit?: boolean;
}): string;
export declare function sparseCheckout(repo: string, specPath: string, ref?: string): {
    workdir: string;
    commit: string;
    specContent: string;
};
