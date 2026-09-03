import { Hover } from "../../types";
import { ContextProviderFactory } from "./complete";
import { NodeDesc } from "./schema";
export declare function hover(filename: string, input: string, pos: number, schema: NodeDesc, contextProviderFactory: ContextProviderFactory): Promise<Hover | undefined>;
