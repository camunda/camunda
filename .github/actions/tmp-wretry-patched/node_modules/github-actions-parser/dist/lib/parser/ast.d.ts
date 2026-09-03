import { Position, YAMLNode, YNode } from "../../types";
import { PropertyPath } from "../utils/path";
export declare const DUMMY_KEY = "dummy";
export declare function inPos(position: Position, pos: number): boolean;
export declare function findNode(node: YAMLNode, pos: number): YAMLNode | null;
export declare function getPathFromNode(node: YNode | null): PropertyPath;
