import { CustomValueProvider } from "../../parser/schema";
import { Context } from "../../../types";
import { TTLCache } from "../../utils/cache";
export declare const actionsInputProvider: (context: Context, cache: TTLCache) => CustomValueProvider;
